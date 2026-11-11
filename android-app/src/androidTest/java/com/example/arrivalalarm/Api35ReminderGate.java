package com.example.arrivalalarm;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inspector.WindowInspector;
import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;

/** Runs against an isolated emulator installation. No user preferences are needed. */
final class Api35ReminderGate {
    private static final String TEST_PACKAGE = "com.navobell.arrivalalarm.debug.p08gate";
    private static final String TAG = "ArrivalApi35Gate";
    private static final int REPEATS = 12;
    private static final AppPrefs.Destination DESTINATION =
            new AppPrefs.Destination("p08-fixture", "P08 fixture", "", "", 40.6892, -74.0445);

    private final Instrumentation instrumentation;
    private final MainActivity activity;
    private final AppPrefs prefs;
    private final ActivityManager activityManager;
    private final Field startPending = field(ReminderRuntimeCoordinator.class, "foregroundStartPending");
    private final Field pendingDestination = field(MainActivity.class, "pendingDestination");
    private final Field pendingResume = field(MainActivity.class, "pendingResume");
    private final Method requestStart = method("requestStart", AppPrefs.Destination.class);
    private final Method confirmClearData = method("confirmClearData");
    private final View activeCard;
    private final TextView activeLabel;
    private final View activeAction;

    static void run(Instrumentation instrumentation, Bundle arguments, Bundle result) throws Exception {
        String targetPackage = instrumentation.getTargetContext().getPackageName();
        check(Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT <= 36 && TEST_PACKAGE.equals(targetPackage),
                "API 31–36 and isolated APK required");
        String mode = arguments.getString("api35ReminderGate");
        if ("home-p1".equals(mode)) {
            HomeP1Checks.run(instrumentation, "true".equals(arguments.getString("expectNight")));
            result.putString("home_p1", "PASS");
            return;
        }
        if ("alarm-layout".equals(mode)) {
            AlarmLayoutChecks.run(instrumentation);
            result.putString("alarm_layout", "PASS");
            return;
        }
        String taskId = arguments.getString("taskId", "");
        boolean revocation = "revocation-verify".equals(mode);
        if ("recovery-verify".equals(mode) || revocation) {
            AppPrefs.ReminderTask saved = new AppPrefs(instrumentation.getTargetContext()).getTask();
            check(sameTask(saved, taskId) && (saved.isActive() || (revocation && saved.isInterrupted())),
                    "fixture was not retained across interruption");
            check(TrackingService.getRuntimeSnapshot().state == ReminderRuntime.State.STOPPED,
                    "tracking runtime must be stopped before opening the Activity");
        }
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(Intent.ACTION_MAIN)
                .setClassName(targetPackage, MainActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        Api35ReminderGate gate = new Api35ReminderGate(instrumentation, activity);
        if (!"permission-denied".equals(mode) && !"revocation-verify".equals(mode)) {
            check(ReminderRuntimeCoordinator.hasCorePermissions(activity), "precise location and notifications required");
        }
        switch (mode) {
            case "true":
                gate.runScenarios(result);
                break;
            case "alarm-lifecycle":
                gate.verifyAlarmLifecycle(result);
                break;
            case "recovery-seed":
                gate.seedRecovery(arguments.getString("recoveryRun", ""));
                break;
            case "recovery-verify":
                gate.verifyRecovery(taskId, result);
                break;
            case "permission-denied":
                gate.verifyPermissionDenied(arguments.getString("permissionCase", ""), result);
                break;
            case "revocation-verify":
                gate.verifyRevocation(taskId, arguments.getString("permissionCase", ""), result);
                break;
            default:
                throw new AssertionError("unknown API 35 gate mode");
        }
    }

    private Api35ReminderGate(Instrumentation instrumentation, MainActivity activity) {
        this.instrumentation = instrumentation;
        this.activity = activity;
        prefs = new AppPrefs(activity);
        activityManager = activity.getSystemService(ActivityManager.class);
        activeCard = (View) read(field(MainActivity.class, "activeCard"), activity);
        activeLabel = (TextView) read(field(MainActivity.class, "activeLabel"), activity);
        activeAction = (View) read(field(MainActivity.class, "activeAction"), activity);
    }

    private void runScenarios(Bundle result) throws Exception {
        onMain(() -> checkStopped("initial state"));

        onMain(this::enqueueStart);
        await("normal start must show 正在守候 with a running foreground service", this::monitoring);
        onMain(() -> {
            tapStop();
            check(taskAndCardCleared(), "normal stop must clear the task and card immediately");
        });
        awaitStopped("normal start/stop");
        result.putInt("normal_start_stop", 1);

        for (int round = 1; round <= REPEATS; round++) {
            onMain(() -> {
                enqueueStart();
                tapStop();
                checkCancelledBeforeDelivery();
            });
            awaitStopped("immediate stop #" + round);
        }
        result.putInt("immediate_stop", REPEATS);

        for (int round = 1; round <= REPEATS; round++) {
            onMain(() -> invoke(confirmClearData));
            instrumentation.waitForIdleSync();
            FutureTask<Void> cancellation = new FutureTask<>(() -> {
                checkCancelledBeforeDelivery();
                check(prefs.getRecents().isEmpty(), "clear must remove the fixture from recents");
            }, null);
            onMain(() -> {
                // AlertDialog posts the positive callback. Queue it before requesting the
                // service so that the real clear handler wins on the next main-loop turn.
                check(findDialogButton("清空").performClick(), "clear confirmation was not clickable");
                new Handler(Looper.getMainLooper()).post(cancellation);
                enqueueStart();
            });
            instrumentation.waitForIdleSync();
            cancellation.get();
            awaitStopped("immediate clear #" + round);
        }
        result.putInt("immediate_clear", REPEATS);

        // A missed Android foreground promotion may crash several seconds after the click.
        SystemClock.sleep(ReminderRuntime.START_TIMEOUT_MS + 1000L);
        onMain(() -> checkStopped("after foreground startup deadline"));
        result.putString("settled", "service absent, task absent, active card hidden");
    }

    private void seedRecovery(String runId) throws Exception {
        check(runId.matches("[a-f0-9]{32}"), "invalid recovery run ID");
        File killSignal = new File(activity.getFilesDir(), "gate-kill-" + runId);
        onMain(() -> checkStopped("before seeding recovery"));
        onMain(this::enqueueStart);
        await("prepare recovery with a running foreground service and a location fix", this::monitoring);
        onMain(() -> Log.i(TAG, "RECOVERY_READY " + runId + " " + Process.myPid() + " " + prefs.getTask().id));
        // Exit by signal only after the host has verified the live fixture. Android versions can
        // restrict run-as from signalling an app process directly, even with the same UID.
        long deadline = SystemClock.elapsedRealtime() + 55_000L;
        do {
            if (killSignal.exists()) Process.killProcess(Process.myPid());
            SystemClock.sleep(100L);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("host did not interrupt the active fixture");
    }

    private void verifyRecovery(String taskId, Bundle result) throws Exception {
        onMain(() -> checkNeedsRecovery(taskId));
        long beforeResume = SystemClock.elapsedRealtime();
        onMain(() -> {
            tapRestore();
            AppPrefs.ReminderTask task = prefs.getTask();
            check(sameTask(task, taskId) && task.isStarting() && pending(), "restore did not queue the same task");
        });
        await("manual restore must reestablish tracking and receive a location", () ->
                monitoring() && sameTask(prefs.getTask(), taskId)
                        && TrackingService.getRuntimeSnapshot().lastLocationElapsedRealtime >= beforeResume);
        onMain(this::tapStop);
        awaitStopped("stop after manual restore");
        result.putInt("recovery_pid", Process.myPid());
        result.putString("recovered", "same task, manual restore, foreground service and location ready");
    }

    private void verifyRevocation(String taskId, String permissionCase, Bundle result) throws Exception {
        boolean notificationDenied = "notification".equals(permissionCase);
        check(notificationDenied || "approximate-only".equals(permissionCase), "unknown revocation case");
        onMain(() -> {
            checkPermissionFixture(notificationDenied, false);
            checkNeedsRecovery(taskId);
            tapRestore();
        });
        if (Build.VERSION.SDK_INT >= 33) {
            expectPermissionDenied(notificationDenied, false);
        } else {
            instrumentation.waitForIdleSync();
            onMain(() -> {
                checkPermissionExplanation(notificationDenied);
                check(findDialogButton("暂不", android.R.id.button2).performClick(),
                        "permission cancel button was not clickable");
            });
            instrumentation.waitForIdleSync();
        }
        onMain(() -> {
            checkPermissionFixture(notificationDenied, false);
            checkNeedsRecovery(taskId);
            check(!(Boolean) read(pendingResume, activity), "denied restore is still pending");
        });
        result.putString("revoked", "same task needs restore, permission hint shown, service absent");
    }

    private void checkNeedsRecovery(String taskId) {
        AppPrefs.ReminderTask task = prefs.getTask();
        check(sameTask(task, taskId) && task.isInterrupted(), "reopening must mark the same task interrupted");
        check(!pending() && trackingService() == null, "service must wait for the user to restore it");
        String label = activeLabel.getText().toString();
        check(activeCard.isShown() && activeLabel.isShown() && label.contains("需要恢复")
                && !label.contains("正在守候"), "reopened UI must show 需要恢复");
    }

    private void tapRestore() {
        check(activeAction.isShown() && "恢复".contentEquals(((TextView) activeAction).getText())
                && activeAction.performClick(), "visible restore action was not clickable");
    }

    private static boolean sameTask(AppPrefs.ReminderTask task, String taskId) {
        return task != null && task.id.equals(taskId) && DESTINATION.id.equals(task.destination.id);
    }

    private void verifyPermissionDenied(String permissionCase, Bundle result) throws Exception {
        boolean firstPrompt = permissionCase.endsWith("-first");
        boolean notificationDenied = "notification".equals(permissionCase) || "notification-first".equals(permissionCase);
        check(notificationDenied || "approximate-only".equals(permissionCase)
                || "approximate-only-first".equals(permissionCase), "unknown permission case");
        checkPermissionFixture(notificationDenied, firstPrompt && !notificationDenied);
        onMain(() -> {
            checkStopped("before requesting permissions");
            invoke(requestStart, DESTINATION);
            checkStopped("after asking for missing permissions");
        });
        expectPermissionDenied(notificationDenied, firstPrompt);
        onMain(() -> {
            checkPermissionFixture(notificationDenied, false);
            checkStopped("after Android denied the required permission");
            check(read(pendingDestination, activity) == null, "denied request still has a pending destination");
        });
        result.putString("permission_case", permissionCase);
        result.putString("permission_blocked", "specific hint shown, task absent, service absent, active card hidden");
    }

    private void expectPermissionDenied(boolean notificationDenied, boolean firstPrompt) throws Exception {
        instrumentation.waitForIdleSync();
        onMain(() -> checkPermissionExplanation(notificationDenied));

        String expected = notificationDenied ? "必须允许通知才能弹出到站闹钟" : "必须允许精确定位才能提醒到站";
        FutureTask<Void> request = new FutureTask<>(() ->
                check(findDialogButton("继续").performClick(), "permission continue button was not clickable"), null);
        UiAutomation automation = instrumentation.getUiAutomation();
        if (firstPrompt) {
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            automation.setServiceInfo(info);
        }
        AccessibilityEvent toast = automation.executeAndWaitForEvent(
                () -> {
                    instrumentation.runOnMainSync(request);
                    if (firstPrompt && notificationDenied) {
                        clickPermissionControl(automation, "permission_deny_button");
                    } else if (firstPrompt) {
                        clickPermissionControl(automation, "permission_location_accuracy_radio_coarse");
                        clickPermissionControl(automation, "permission_allow_foreground_only_button");
                    }
                },
                event -> event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                        && event.getText().stream().anyMatch(expected::contentEquals), 8000L);
        toast.recycle();
        request.get();
    }

    private void checkPermissionExplanation(boolean notificationDenied) {
        View message = findDialogButton("继续").getRootView().findViewById(android.R.id.message);
        String reason = notificationDenied ? "系统通知" : "精确位置";
        check(message instanceof TextView && message.isShown()
                && ((TextView) message).getText().toString().contains(reason), "missing permission explanation");
    }

    private static void clickPermissionControl(UiAutomation automation, String id) {
        long deadline = SystemClock.elapsedRealtime() + 5000L;
        do {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null && root.getPackageName() != null
                    && root.getPackageName().toString().endsWith(".permissioncontroller")) {
                // Google's controller uses the AOSP resource namespace with a different application ID.
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(
                        "com.android.permissioncontroller:id/" + id)) {
                    if (node.isVisibleToUser() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
                }
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("could not click Android permission control " + id);
    }

    private void checkPermissionFixture(boolean notificationDenied, boolean noLocationGrantYet) {
        check(granted(Manifest.permission.ACCESS_COARSE_LOCATION) != noLocationGrantYet, "unexpected coarse location grant");
        check(granted(Manifest.permission.ACCESS_FINE_LOCATION) == notificationDenied, "unexpected precise location grant");
        if (Build.VERSION.SDK_INT >= 33) {
            check(granted(Manifest.permission.POST_NOTIFICATIONS) != notificationDenied, "unexpected notification grant");
        }
    }

    private boolean granted(String permission) {
        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean monitoring() {
        ActivityManager.RunningServiceInfo service = trackingService();
        AppPrefs.ReminderTask task = prefs.getTask();
        ReminderRuntime.Snapshot runtime = TrackingService.getRuntimeSnapshot();
        return service != null && service.foreground && task != null && task.isActive()
                && runtime.state == ReminderRuntime.State.LOCATION_READY && runtime.registeredProviderCount > 0
                && runtime.lastLocationElapsedRealtime > 0L && activeCard.isShown() && activeLabel.isShown()
                && activeLabel.getText().toString().contains("正在守候");
    }

    private void enqueueStart() {
        invoke(requestStart, DESTINATION);
        AppPrefs.ReminderTask task = prefs.getTask();
        check(task != null && task.isStarting() && pending(), "start command was not queued");
    }

    private void verifyAlarmLifecycle(Bundle result) throws Exception {
        check(!alarmAudioPlaying(), "another alarm is already playing on the fixture");
        onMain(this::enqueueStart);
        await("first arrival must show a single alarm and stop location updates", this::hasActiveAlarm);
        Notification first = alarmNotification().getNotification();
        check(activity.getSystemService(NotificationManager.class)
                        .getNotificationChannel(first.getChannelId()).getSound() == null,
                "the confirmation channel must not supply alarm audio");
        await("alarm must play on the system alarm stream", this::alarmAudioPlaying);
        check("到站提醒已触发".contentEquals(first.extras.getCharSequence(Notification.EXTRA_TITLE)),
                "initial notification must hide the destination");
        PendingIntent staleAction = first.actions[0].actionIntent;
        staleAction.send();
        awaitStopped("arrival notification action");
        await("arrival confirmation must stop audio", () -> !alarmAudioPlaying());
        await("first alarm notification must be gone", () -> alarmNotification() == null);

        onMain(this::enqueueStart);
        await("second arrival must show a single alarm", this::hasActiveAlarm);
        await("second arrival must start alarm audio", this::alarmAudioPlaying);
        long postedAt = alarmNotification().getPostTime();
        staleAction.send();
        instrumentation.waitForIdleSync();
        check(hasActiveAlarm(), "an old notification action affected the new task");
        SystemClock.sleep(32_000L);
        check(hasActiveAlarm() && alarmAudioPlaying() && alarmNotification().getPostTime() == postedAt,
                "alarm must keep playing without reposting its notification");
        Log.i(TAG, "alarm playback active after 32 seconds, waiting for automatic stop");
        await("alarm window must end with notification retained and service stopped", 290_000L, () -> {
            AppPrefs.ReminderTask task = prefs.getTask();
            StatusBarNotification alarm = alarmNotification();
            return trackingService() == null && task != null && task.isAlerting()
                    && alarm != null && (alarm.getNotification().flags & Notification.FLAG_FOREGROUND_SERVICE) == 0;
        });
        await("the alarm timeout must stop audio", () -> !alarmAudioPlaying());
        Notification detached = alarmNotification().getNotification();
        onMain(() -> {
            prefs.setShowDestinationInNotifications(true);
            ArrivalNotification.refreshIfPosted(activity, prefs, prefs.getTask());
        });
        await("detached notification privacy must update quietly", () -> {
            Notification updated = alarmNotification().getNotification();
            return (updated.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0
                    && updated.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains(DESTINATION.name)
                    && "到站提醒已触发".contentEquals(updated.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE));
        });
        detached.actions[0].actionIntent.send();
        awaitStopped("detached alarm confirmation");
        await("detached alarm notification must be gone", () -> alarmNotification() == null);
        result.putString("alarm_lifecycle", "PASS");
    }

    private boolean alarmAudioPlaying() {
        return activity.getSystemService(AudioManager.class).getActivePlaybackConfigurations().stream()
                .anyMatch(config -> config.getAudioAttributes().getUsage() == AudioAttributes.USAGE_ALARM);
    }

    private boolean hasActiveAlarm() {
        AppPrefs.ReminderTask task = prefs.getTask();
        ActivityManager.RunningServiceInfo service = trackingService();
        return task != null && task.isAlerting() && service != null && service.foreground
                && TrackingService.getRuntimeSnapshot().registeredProviderCount == 0
                && alarmNotification() != null && postedNotifications().length == 1;
    }

    private StatusBarNotification alarmNotification() {
        for (StatusBarNotification posted : postedNotifications()) {
            if (posted.getId() == ArrivalNotification.ID) return posted;
        }
        return null;
    }

    private StatusBarNotification[] postedNotifications() {
        return activity.getSystemService(NotificationManager.class).getActiveNotifications();
    }

    private void tapStop() {
        check(activeAction.isShown() && "停止".contentEquals(((TextView) activeAction).getText())
                && activeAction.performClick(), "visible stop action was not clickable");
    }

    private TextView findDialogButton(String label) {
        return findDialogButton(label, android.R.id.button1);
    }

    private TextView findDialogButton(String label, int buttonId) {
        for (View window : WindowInspector.getGlobalWindowViews()) {
            View button = window.findViewById(buttonId);
            if (button instanceof TextView && button.isShown() && label.contentEquals(((TextView) button).getText())) {
                return (TextView) button;
            }
        }
        throw new AssertionError("missing dialog button: " + label);
    }

    private void checkCancelledBeforeDelivery() {
        check(pending() && taskAndCardCleared(),
                "cancellation must clear the task and card before Android delivers the service");
    }

    private void awaitStopped(String scenario) throws Exception {
        await(scenario, this::stopped);
        Log.i(TAG, scenario + " PASS: service absent, task absent, active card hidden");
    }

    private boolean stopped() {
        return !pending() && taskAndCardCleared() && trackingService() == null
                && TrackingService.getRuntimeSnapshot().state == ReminderRuntime.State.STOPPED;
    }

    private boolean taskAndCardCleared() { return prefs.getTask() == null && !activeCard.isShown(); }

    private void checkStopped(String scenario) {
        check(stopped(), scenario + ": service, task or active card still present");
    }

    @SuppressWarnings("deprecation") // On API 35 getRunningServices returns this app's own services.
    private ActivityManager.RunningServiceInfo trackingService() {
        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (TrackingService.class.getName().equals(service.service.getClassName())) return service;
        }
        return null;
    }

    private boolean pending() { return (Boolean) read(startPending, null); }

    private void await(String scenario, BooleanSupplier condition) throws Exception {
        await(scenario, 8000L, condition);
    }

    private void await(String scenario, long timeoutMs, BooleanSupplier condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        do {
            FutureTask<Boolean> sample = new FutureTask<>(condition::getAsBoolean);
            instrumentation.runOnMainSync(sample);
            if (sample.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError(scenario + " timed out");
    }

    private void onMain(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        instrumentation.runOnMainSync(task);
        task.get();
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field result = owner.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Object read(Field field, Object owner) {
        try { return field.get(owner); }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
    }

    private static Method method(String name, Class<?>... arguments) {
        try {
            Method result = MainActivity.class.getDeclaredMethod(name, arguments);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private void invoke(Method method, Object... arguments) {
        try { method.invoke(activity, arguments); }
        catch (InvocationTargetException error) { throw new AssertionError(error.getCause()); }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
