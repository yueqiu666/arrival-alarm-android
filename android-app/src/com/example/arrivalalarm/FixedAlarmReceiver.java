package com.example.arrivalalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

public final class FixedAlarmReceiver extends BroadcastReceiver {
    public static final String ACTION_TRIGGER = "com.example.arrivalalarm.FIXED_ALARM_TRIGGER";
    public static final String EXTRA_FIXED_ALARM_ID = "fixed_alarm_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_TRIGGER.equals(intent.getAction())) return;
        AppPrefs prefs = new AppPrefs(context);
        AppPrefs.FixedAlarm alarm = prefs.getFixedAlarm(intent.getStringExtra(EXTRA_FIXED_ALARM_ID));
        if (alarm == null || !alarm.enabled) return;
        ScheduleManager.schedule(context, alarm);

        if (prefs.getTask() != null) {
            FixedAlarmPrompt.show(context, alarm, "已有一个到站提醒正在守候，本次没有覆盖。点按可查看。 ");
            return;
        }
        boolean fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean notifications = Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean background = Build.VERSION.SDK_INT < 29
                || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine || !notifications || !background) {
            FixedAlarmPrompt.show(context, alarm, "需要补充定位或通知权限。点按后会打开应用，并立即预设这个目的地。");
            return;
        }

        long now = System.currentTimeMillis();
        AppPrefs.ReminderTask task = new AppPrefs.ReminderTask("fixed-task-" + now, alarm.destination,
                prefs.getRadius(), AppPrefs.ReminderTask.STATUS_STARTING, now);
        prefs.saveTask(task);
        prefs.addRecent(alarm.destination);
        if (!ReminderRuntimeCoordinator.startReminder(context)) {
            FixedAlarmPrompt.show(context, alarm, "系统限制了后台自动启动。点按即可立即开始守候。");
        }
    }
}
