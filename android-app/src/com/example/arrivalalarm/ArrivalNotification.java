package com.example.arrivalalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;

final class ArrivalNotification {
    static final int ID = 101;
    private static final String CHANNEL = "arrival_alarm";
    static final String EXTRA_TASK_ID = "arrival_task_id";

    private ArrivalNotification() { }

    static void createChannel(NotificationManager manager) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "到站闹钟", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("接近目的地时发出声音和振动");
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 500, 180, 500, 180, 900});
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        manager.createNotificationChannel(channel);
    }

    static Notification build(Context context, AppPrefs prefs, AppPrefs.ReminderTask task, boolean quietUpdate) {
        Uri taskUri = Uri.fromParts("arrivalalarm-task", task.id, null);
        Intent alarm = new Intent(context, AlarmActivity.class)
                .setData(taskUri).putExtra(EXTRA_TASK_ID, task.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 40, alarm, PendingIntent.FLAG_IMMUTABLE);
        Intent arrivedIntent = new Intent(context, ActionReceiver.class).setAction(ActionReceiver.ACTION_ARRIVED)
                .setData(taskUri).putExtra(EXTRA_TASK_ID, task.id);
        PendingIntent arrived = PendingIntent.getBroadcast(context, 41, arrivedIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.rgb(242, 187, 98))
                .setContentTitle(prefs.showDestinationInNotifications()
                        ? context.getString(R.string.arrival_title, task.destination.name)
                        : context.getString(R.string.notification_private_arrival))
                .setContentText(context.getString(R.string.notification_arrival_detail))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(NotificationPrivacy.publicVersion(context, CHANNEL,
                        context.getString(R.string.notification_private_arrival)))
                .setOngoing(true)
                .setOnlyAlertOnce(quietUpdate)
                .setContentIntent(content)
                .setDeleteIntent(arrived)
                .addAction(new Notification.Action.Builder(null, context.getString(R.string.arrival_acknowledge), arrived).build());
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return builder.build();
    }

    static void refreshIfPosted(Context context, AppPrefs prefs, AppPrefs.ReminderTask task) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        for (StatusBarNotification posted : manager.getActiveNotifications()) {
            if (posted.getId() == ID) {
                manager.notify(ID, build(context, prefs, task, true));
                return;
            }
        }
    }

    static void cancel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(ID);
    }
}
