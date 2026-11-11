package com.example.arrivalalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.NotificationManager;

public final class ActionReceiver extends BroadcastReceiver {
    public static final String ACTION_ARRIVED = "com.example.arrivalalarm.ARRIVED";
    public static final String ACTION_STOP_REMINDER = "com.example.arrivalalarm.NOTIFICATION_STOP";
    public static final String ACTION_RESUME_REMINDER = "com.example.arrivalalarm.NOTIFICATION_RESUME";
    public static final String ACTION_DISMISS_JOURNEY = "com.example.arrivalalarm.DISMISS_JOURNEY";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_DISMISS_JOURNEY.equals(action)) {
            AppPrefs prefs = new AppPrefs(context);
            CoreLogic.JourneyDetector detector = prefs.getJourneyDetector();
            if (detector != null) {
                detector.state = CoreLogic.JourneyDetector.DISMISSED;
                prefs.setJourneyRuntime(detector);
            }
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(JourneyPrompt.NOTIFICATION_ID);
            return;
        }
        if (ACTION_ARRIVED.equals(action)) {
            ReminderRuntimeCoordinator.acknowledgeArrival(context, intent.getStringExtra(ArrivalNotification.EXTRA_TASK_ID));
            return;
        }
        if (ACTION_STOP_REMINDER.equals(action)) {
            ReminderRuntimeCoordinator.stopReminder(context);
            return;
        }
        if (ACTION_RESUME_REMINDER.equals(action)) {
            if (!ReminderRuntimeCoordinator.hasCorePermissions(context)) {
                Intent open = new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(open);
                return;
            }
            ReminderRuntimeCoordinator.resumeReminder(context);
        }
    }
}
