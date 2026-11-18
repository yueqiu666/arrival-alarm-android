package com.example.arrivalalarm;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

final class NotificationReadinessChecks {
    private NotificationReadinessChecks() { }

    static void run(Instrumentation instrumentation, String scenario) throws ReflectiveOperationException {
        Context target = instrumentation.getTargetContext();
        NotificationManager manager = target.getSystemService(NotificationManager.class);
        check(ReminderPermissions.hasLocation(target), "fixture needs precise location permission");
        boolean permissionDenied = "permission-denied".equals(scenario);
        check(ReminderRuntimeCoordinator.hasCorePermissions(target) != permissionDenied,
                "notification display settings must not change the reminder launch policy");

        String expectedText;
        String expectedAction;
        String channelId = null;
        switch (scenario) {
            case "permission-denied":
                expectedText = "通知权限";
                expectedAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
                break;
            case "app-off":
                check(!manager.areNotificationsEnabled(), "fixture app notifications must be off");
                expectedText = "应用通知已关闭";
                expectedAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS;
                break;
            case "channel-off":
                check(manager.areNotificationsEnabled(), "fixture app notifications must be on");
                channelId = "arrival_confirmation";
                manager.createNotificationChannel(new NotificationChannel(channelId, "到站确认", NotificationManager.IMPORTANCE_NONE));
                check(manager.getNotificationChannel(channelId).getImportance() == NotificationManager.IMPORTANCE_NONE,
                        "fixture arrival confirmation channel must be off");
                expectedText = "到站确认";
                expectedAction = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS;
                break;
            case "ready":
                check(manager.areNotificationsEnabled(), "fixture app notifications must be on");
                expectedText = null;
                expectedAction = null;
                break;
            default:
                throw new AssertionError("unknown notification readiness case");
        }

        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(target, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        AtomicReference<Intent> opened = new AtomicReference<>();
        Instrumentation.ActivityMonitor settings = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                opened.set(new Intent(intent));
                return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        instrumentation.addMonitor(settings);
        try {
            Field field = MainActivity.class.getDeclaredField("permissionStatus");
            field.setAccessible(true);
            Button button = (Button) field.get(activity);
            if (expectedText == null) {
                check(button.getVisibility() == View.GONE, "ready state must hide the warning");
                return;
            }
            check(button.isShown() && button.getText().toString().contains(expectedText),
                    "wrong or missing readiness warning: " + button.getText());
            instrumentation.runOnMainSync(button::performClick);
            Intent intent = opened.get();
            check(intent != null && expectedAction.equals(intent.getAction()), "wrong settings entry");
            if (Settings.ACTION_APPLICATION_DETAILS_SETTINGS.equals(expectedAction)) {
                check(intent.getData() != null && target.getPackageName().equals(intent.getData().getSchemeSpecificPart()),
                        "permissions settings must open for the app");
            } else {
                check(target.getPackageName().equals(intent.getStringExtra(Settings.EXTRA_APP_PACKAGE)),
                        "notification settings must open for the app");
                if (channelId != null) check(channelId.equals(intent.getStringExtra(Settings.EXTRA_CHANNEL_ID)),
                        "channel settings must open for the blocked channel");
            }
        } finally {
            instrumentation.removeMonitor(settings);
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
