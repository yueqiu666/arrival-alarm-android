package com.example.arrivalalarm;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.inspector.WindowInspector;
import android.widget.Button;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

final class RestoreNotificationChecks {
    private RestoreNotificationChecks() { }

    static void run(Instrumentation instrumentation, String scenario) throws PendingIntent.CanceledException {
        Context context = instrumentation.getTargetContext();
        check(ReminderPermissions.hasNotifications(context), "notifications must be granted for the fixture");
        if ("prepare".equals(scenario)) {
            // Leave a notification for a manual click through the real system shade.
            instrumentation.runOnMainSync(() -> postFixture(context, false));
            return;
        }
        boolean missingLocation = "missing-location".equals(scenario);
        boolean stale = "stale".equals(scenario);
        check(missingLocation || stale || "granted".equals(scenario), "unknown restore case");
        check(ReminderPermissions.hasLocation(context) != missingLocation, "incorrect fixture location permission");

        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        AppPrefs prefs = new AppPrefs(context);
        try {
            instrumentation.runOnMainSync(() -> postFixture(context, stale));
            await("posted restore notification", () -> restoreAction(context) != null);
            PendingIntent action = restoreAction(context);
            check(action != null && action.isActivity(), "restore button must directly open an Activity");
            action.send();
            await("restore delivered to MainActivity", () -> onMain(instrumentation,
                    () -> ReminderRuntimeCoordinator.ACTION_RESUME_REMINDER.equals(activity.getIntent().getAction())));

            if (missingLocation) {
                await("existing permission explanation", () -> onMain(instrumentation,
                        () -> hasPermissionExplanation(context)));
            }
            if (missingLocation || stale) {
                instrumentation.waitForIdleSync();
                AppPrefs.ReminderTask task = prefs.getTask();
                check(task != null && "current-task".equals(task.id) && task.isInterrupted(),
                        "invalid restore changed the interrupted task");
                check(TrackingService.getRuntimeSnapshot().state == ReminderRuntime.State.STOPPED,
                        "invalid restore started tracking");
            } else {
                await("same reminder running", () -> {
                    AppPrefs.ReminderTask task = prefs.getTask();
                    ReminderRuntime.Snapshot runtime = TrackingService.getRuntimeSnapshot();
                    return task != null && "current-task".equals(task.id) && task.isActive()
                            && runtime.state == ReminderRuntime.State.LOCATION_READY && runtime.registeredProviderCount > 0;
                });
            }
        } finally {
            instrumentation.runOnMainSync(() -> {
                ReminderRuntimeCoordinator.stopReminder(context);
                activity.finish();
            });
        }
    }

    private static void postFixture(Context context, boolean stale) {
        ReminderRuntimeCoordinator.stopReminder(context);
        AppPrefs prefs = new AppPrefs(context);
        AppPrefs.Destination place = new AppPrefs.Destination("restore-fixture", "测试车站", "", "", 40.6892, -74.0445);
        AppPrefs.ReminderTask saved = new AppPrefs.ReminderTask("current-task", place, 300,
                AppPrefs.ReminderTask.STATUS_INTERRUPTED, System.currentTimeMillis());
        prefs.saveTask(saved);
        AppPrefs.ReminderTask notificationTask = stale
                ? new AppPrefs.ReminderTask("old-task", place, 300, saved.status, saved.startedAt) : saved;
        ReminderRuntimeCoordinator.showRestoreNotification(context, notificationTask, "");
    }

    private static PendingIntent restoreAction(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (StatusBarNotification posted : manager.getActiveNotifications()) {
            if (posted.getId() == ReminderRuntimeCoordinator.RESTORE_NOTIFICATION_ID
                    && posted.getNotification().actions != null && posted.getNotification().actions.length == 1) {
                return posted.getNotification().actions[0].actionIntent;
            }
        }
        return null;
    }

    private static boolean hasPermissionExplanation(Context context) {
        String label = context.getString(R.string.permission_continue);
        for (View window : WindowInspector.getGlobalWindowViews()) {
            View button = window.findViewById(android.R.id.button1);
            if (button instanceof Button && button.isShown() && label.contentEquals(((Button) button).getText())) return true;
        }
        return false;
    }

    private static boolean onMain(Instrumentation instrumentation, BooleanSupplier condition) {
        AtomicBoolean result = new AtomicBoolean();
        instrumentation.runOnMainSync(() -> result.set(condition.getAsBoolean()));
        return result.get();
    }

    private static void await(String label, BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 10_000L;
        do {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(100L);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("timed out waiting for " + label);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
