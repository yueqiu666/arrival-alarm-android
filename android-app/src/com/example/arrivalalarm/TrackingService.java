package com.example.arrivalalarm;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Locale;

public final class TrackingService extends Service implements LocationListener {
    public static final String ACTION_START_REMINDER = "com.example.arrivalalarm.START_REMINDER";
    public static final String ACTION_STATUS = "com.example.arrivalalarm.STATUS";
    public static final String PERMISSION_STATUS = BuildConfig.APPLICATION_ID + ".permission.INTERNAL_STATUS";
    public static final String EXTRA_FOCUS_DESTINATION = "focus_destination";

    private static final String CHANNEL_TRACKING = "tracking";
    private static final int FOREGROUND_ID = 100;
    private static final long REMINDER_HEARTBEAT_INTERVAL_MS = 60_000L;
    private static final long ALARM_DURATION_MS = 5 * 60_000L;
    private static volatile ReminderRuntime.Snapshot runtimeSnapshot = ReminderRuntime.Snapshot.stopped();
    private static volatile long runtimeGeneration;
    // All writes and UI reads use the main thread. Location distance is display-only.
    private static String distanceTaskId;
    private static double liveDistanceMeters = Double.NaN;
    private AppPrefs prefs;
    private LocationManager locationManager;
    private NotificationManager notifications;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CoreLogic.ArrivalTracker arrivalTracker;
    private long lastAcceptedFixNanos;
    private ArrivalAlarm arrivalAlarm;
    private String alarmTaskId;
    private final ReminderProviderRegistry providerRegistry = new ReminderProviderRegistry();
    private long serviceGeneration;
    private boolean acceptingLocationCallbacks;
    private int locationRecoveryAttempt;
    private long locationRecoveryStartedElapsedRealtime;
    private boolean locationRecoveryForcePeriodic;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (shared, key) -> {
        if ((AppPrefs.TASK.equals(key) || key == null) && alarmTaskId != null) {
            AppPrefs.ReminderTask task = prefs.getTask();
            if (task == null || !task.isAlerting() || !alarmTaskId.equals(task.id)) arrivalAlarm.stop();
        }
        if (!AppPrefs.SHOW_DESTINATION.equals(key) || !ownsCurrentRuntime()
                || runtimeSnapshot.state == ReminderRuntime.State.STOPPED
                || runtimeSnapshot.state == ReminderRuntime.State.STARTING
                || runtimeSnapshot.state == ReminderRuntime.State.FAILED) return;
        AppPrefs.ReminderTask task = prefs.getTask();
        if (task == null || task.isInterrupted() || notifications == null) return;
        if (task.isAlerting()) {
            if (task.id.equals(alarmTaskId)) {
                ArrivalNotification.refreshIfPosted(this, prefs, task);
            }
            return;
        }
        Notification notification = runtimeSnapshot.state == ReminderRuntime.State.RECOVERING
                ? buildRecoveryNotification(task, locationRecoveryAttempt) : buildTrackingNotification(task);
        notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
        notifications.notify(FOREGROUND_ID, notification);
    };

    private final Runnable alarmTimeout = new Runnable() {
        @Override public void run() {
            arrivalAlarm.stop();
            AppPrefs.ReminderTask task = prefs.getTask();
            if (!ownsCurrentRuntime() || task == null || !task.isAlerting() || !task.id.equals(alarmTaskId)) return;
            stopEverything(STOP_FOREGROUND_DETACH);
        }
    };

    private final Runnable reminderHeartbeat = new Runnable() {
        @Override public void run() {
            if (!ownsCurrentRuntime()) return;
            AppPrefs.ReminderTask task = prefs.getTask();
            if (task == null || task.isInterrupted()) return;
            long now = SystemClock.elapsedRealtime();
            if (runtimeSnapshot.state == ReminderRuntime.State.LOCATION_READY
                    && runtimeSnapshot.registeredProviderCount > 0
                    && (runtimeSnapshot.lastLocationElapsedRealtime == 0L
                    ? !runtimeSnapshot.isWaitingForFirstLocationWithinTimeout(now)
                    : !runtimeSnapshot.isLocationFresh(now))) {
                startLocationRecovery(now);
            } else {
                publishRuntime(runtimeSnapshot.heartbeat(now));
            }
            if (ownsCurrentRuntime() && runtimeSnapshot.state != ReminderRuntime.State.STOPPED
                    && runtimeSnapshot.state != ReminderRuntime.State.FAILED) {
                handler.postDelayed(this, REMINDER_HEARTBEAT_INTERVAL_MS);
            }
        }
    };

    private final Runnable locationRecoveryRetry = new Runnable() {
        @Override public void run() {
            if (!ownsCurrentRuntime() || runtimeSnapshot.state != ReminderRuntime.State.RECOVERING) return;
            AppPrefs.ReminderTask task = prefs.getTask();
            if (task == null || (!task.isActive() && !task.isStarting())) return;
            if (runtimeSnapshot.lastLocationElapsedRealtime > 0L
                    && runtimeSnapshot.lastLocationElapsedRealtime >= locationRecoveryStartedElapsedRealtime) {
                finishLocationRecovery(task);
                return;
            }
            if (LocationRecoveryPolicy.hasAttemptsRemaining(locationRecoveryAttempt)) {
                attemptLocationRecovery(SystemClock.elapsedRealtime());
            } else {
                failReminder(ReminderRuntime.Failure.LOCATION_STALE,
                        "定位多次自动恢复失败，请检查定位开关或系统省电设置。");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        serviceGeneration = runtimeGeneration;
        runtimeSnapshot = ReminderRuntime.Snapshot.starting(SystemClock.elapsedRealtime());
        prefs = new AppPrefs(this);
        prefs.registerPreferenceListener(prefsListener);
        prefs.disableDeferredAutomation();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        arrivalAlarm = new ArrivalAlarm(this);
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceGeneration = runtimeGeneration;
        if (intent != null && ACTION_START_REMINDER.equals(intent.getAction())) {
            ReminderRuntimeCoordinator.onForegroundStartDelivered();
            AppPrefs.ReminderTask task = prefs.getTask();
            if (task == null || task.isInterrupted()) finishCancelledStart();
            else startReminderMode(task);
        } else {
            stopEverything();
        }
        sendStatus();
        return START_NOT_STICKY;
    }

    private void finishCancelledStart() {
        // Android still expects a promotion when the reminder disappeared before delivery.
        try {
            promoteForeground(new Notification.Builder(this, CHANNEL_TRACKING)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("正在结束到站提醒")
                    .build(), false);
        } catch (RuntimeException rejected) {
            // Permissions or foreground eligibility may have changed since the request.
        }
        stopEverything();
    }

    private void startReminderMode(AppPrefs.ReminderTask task) {
        resetLocationRecovery();
        if (task.isInterrupted()) {
            stopEverything();
            return;
        }
        if (task.isAlerting()) {
            if (!task.id.equals(alarmTaskId)) showArrivalAlert(task);
            return;
        }
        stopArrivalAlarm();
        startReminderHeartbeat();
        lastAcceptedFixNanos = 0;
        arrivalTracker = new CoreLogic.ArrivalTracker(task.destination.latitude, task.destination.longitude, task.radiusMeters);
        try {
            promoteForeground(buildTrackingNotification(task), false);
        } catch (RuntimeException failed) {
            failReminder(ReminderRuntime.Failure.FOREGROUND_START, "系统暂时不允许启动定位服务。");
            return;
        }
        publishRuntime(ReminderRuntime.Snapshot.foreground(
                SystemClock.elapsedRealtime(), SystemClock.elapsedRealtime()));
        requestLocationUpdates(false);
    }

    private void requestLocationUpdates(boolean recovery) {
        requestLocationUpdates(recovery, recovery, false);
    }

    private void requestLocationUpdates(boolean recovery,
                                        boolean forcePeriodic, boolean preserveRuntime) {
        if (!ownsCurrentRuntime()) return;
        AppPrefs.ReminderTask task = prefs.getTask();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            failReminder(ReminderRuntime.Failure.LOCATION_PERMISSION, "请重新允许精确定位。");
            return;
        }
        if (locationManager == null) {
            failReminder(ReminderRuntime.Failure.LOCATION_LISTENER, "设备没有可用的定位服务。");
            return;
        }
        if (!preserveRuntime) removeLocationUpdates();
        long minTime = forcePeriodic ? 15_000L : 5_000L;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, 0f, this, Looper.getMainLooper());
                providerRegistry.add(LocationManager.GPS_PROVIDER);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 15_000L, 0f, this, Looper.getMainLooper());
                providerRegistry.add(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException ignored) {
            failReminder(ReminderRuntime.Failure.LOCATION_PERMISSION, "请重新允许精确定位。");
            return;
        } catch (IllegalArgumentException ignored) {
            if (providerRegistry.count() == 0) {
                if (task != null && (task.isActive() || task.isStarting())) {
                    if (!recovery) {
                        startLocationRecovery(SystemClock.elapsedRealtime());
                    } else if (LocationRecoveryPolicy.hasAttemptsRemaining(locationRecoveryAttempt)) {
                        scheduleLocationRecoveryRetry(locationRecoveryAttempt);
                    } else {
                        failReminder(ReminderRuntime.Failure.LOCATION_LISTENER, "设备暂时无法建立定位监听。");
                    }
                } else {
                    failReminder(ReminderRuntime.Failure.LOCATION_LISTENER, "设备暂时无法建立定位监听。");
                }
                return;
            }
        }
        if (providerRegistry.count() == 0) {
            failReminder(ReminderRuntime.Failure.NO_LOCATION_PROVIDER, "请打开定位后再恢复守候。");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        acceptingLocationCallbacks = true;
        if (recovery && task != null && task.isActive()) {
            locationRecoveryForcePeriodic = forcePeriodic;
            publishRuntime(ReminderRuntime.Snapshot.recovering(
                    providerRegistry.count(), locationRecoveryAttempt, now, now,
                    runtimeSnapshot.lastLocationElapsedRealtime));
            notifications.notify(FOREGROUND_ID, buildRecoveryNotification(task, locationRecoveryAttempt));
        } else {
            if (recovery && task != null) {
                locationRecoveryAttempt = 0;
                locationRecoveryStartedElapsedRealtime = 0L;
                handler.removeCallbacks(locationRecoveryRetry);
                locationRecoveryForcePeriodic = forcePeriodic;
            } else if (!recovery) {
                locationRecoveryForcePeriodic = false;
            }
            if (task != null && task.isStarting()) {
                task.markActive();
                prefs.saveTask(task);
            }
            if (!preserveRuntime) {
                publishRuntime(ReminderRuntime.Snapshot.locationReady(providerRegistry.count(), now, now, 0L));
            }
        }
    }

    private void startLocationRecovery(long nowElapsedRealtime) {
        if (runtimeSnapshot.state == ReminderRuntime.State.RECOVERING) return;
        locationRecoveryAttempt = 0;
        attemptLocationRecovery(nowElapsedRealtime);
    }

    private void attemptLocationRecovery(long nowElapsedRealtime) {
        if (!ownsCurrentRuntime()) return;
        AppPrefs.ReminderTask task = prefs.getTask();
        if (task == null || (!task.isActive() && !task.isStarting())) return;
        locationRecoveryAttempt++;
        locationRecoveryStartedElapsedRealtime = nowElapsedRealtime;
        publishRuntime(ReminderRuntime.Snapshot.recovering(
                providerRegistry.count(), locationRecoveryAttempt, nowElapsedRealtime,
                nowElapsedRealtime, runtimeSnapshot.lastLocationElapsedRealtime));
        notifications.notify(FOREGROUND_ID, buildRecoveryNotification(task, locationRecoveryAttempt));
        requestLocationUpdates(true);
        if (ownsCurrentRuntime() && runtimeSnapshot.state == ReminderRuntime.State.RECOVERING) {
            scheduleLocationRecoveryRetry(locationRecoveryAttempt);
        }
    }

    private void scheduleLocationRecoveryRetry(int attempt) {
        handler.removeCallbacks(locationRecoveryRetry);
        handler.postDelayed(locationRecoveryRetry, LocationRecoveryPolicy.delayAfterAttempt(attempt));
    }

    private void finishLocationRecovery(AppPrefs.ReminderTask task) {
        locationRecoveryAttempt = 0;
        locationRecoveryStartedElapsedRealtime = 0L;
        locationRecoveryForcePeriodic = false;
        handler.removeCallbacks(locationRecoveryRetry);
        notifications.notify(FOREGROUND_ID, buildTrackingNotification(task));
    }

    private void resetLocationRecovery() {
        locationRecoveryAttempt = 0;
        locationRecoveryStartedElapsedRealtime = 0L;
        locationRecoveryForcePeriodic = false;
        handler.removeCallbacks(locationRecoveryRetry);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null || !ownsCurrentRuntime() || !acceptingLocationCallbacks) return;
        AppPrefs.ReminderTask task = prefs.getTask();
        if (task == null || !task.isActive() || providerRegistry.count() == 0) return;
        long fixNanos = location.getElapsedRealtimeNanos();
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : Float.MAX_VALUE;
        CoreLogic.LocationFix fix = new CoreLogic.LocationFix(
                location.getLatitude(), location.getLongitude(), accuracy, -1f, fixNanos / 1_000_000L);
        if (!CoreLogic.usableArrivalFix(fix, task.radiusMeters)
                || !CoreLogic.freshArrivalFix(fixNanos, lastAcceptedFixNanos, SystemClock.elapsedRealtimeNanos())) return;
        lastAcceptedFixNanos = fixNanos;
        long nowElapsedRealtime = SystemClock.elapsedRealtime();
        boolean restoreNormalLocationProfile = false;
        if (runtimeSnapshot.state == ReminderRuntime.State.RECOVERING) {
            restoreNormalLocationProfile = locationRecoveryForcePeriodic;
            finishLocationRecovery(task);
        }
        publishRuntime(runtimeSnapshot.locationReceived(nowElapsedRealtime));
        if (restoreNormalLocationProfile) {
            requestLocationUpdates(false, false, true);
        }
        processReminder(task, fix);
    }

    @Override public void onProviderEnabled(String provider) {
        AppPrefs.ReminderTask task = prefs.getTask();
        if (ownsCurrentRuntime() && task != null && task.isActive()) {
            requestLocationUpdates(runtimeSnapshot.state == ReminderRuntime.State.RECOVERING);
        }
    }
    @Override public void onProviderDisabled(String provider) {
        AppPrefs.ReminderTask task = prefs.getTask();
        if (!ownsCurrentRuntime() || !acceptingLocationCallbacks || task == null || !task.isActive()) return;
        if (!providerRegistry.remove(provider)) return;
        if (providerRegistry.count() == 0) {
            failReminder(ReminderRuntime.Failure.NO_LOCATION_PROVIDER, "请打开定位后再恢复守候。");
        } else if (runtimeSnapshot.state == ReminderRuntime.State.RECOVERING) {
            publishRuntime(ReminderRuntime.Snapshot.recovering(
                    providerRegistry.count(), locationRecoveryAttempt,
                    locationRecoveryStartedElapsedRealtime,
                    runtimeSnapshot.lastHeartbeatElapsedRealtime,
                    runtimeSnapshot.lastLocationElapsedRealtime));
        } else {
            publishRuntime(ReminderRuntime.Snapshot.locationReady(
                    providerRegistry.count(), runtimeSnapshot.stateSinceElapsedRealtime,
                    runtimeSnapshot.lastHeartbeatElapsedRealtime, runtimeSnapshot.lastLocationElapsedRealtime));
        }
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    private void processReminder(AppPrefs.ReminderTask task, CoreLogic.LocationFix fix) {
        if (arrivalTracker == null) arrivalTracker = new CoreLogic.ArrivalTracker(task.destination.latitude, task.destination.longitude, task.radiusMeters);
        CoreLogic.ArrivalResult result = arrivalTracker.update(fix);
        distanceTaskId = task.id;
        liveDistanceMeters = result.distanceMeters;
        if (result.triggered) {
            triggerArrival(task);
        } else {
            notifications.notify(FOREGROUND_ID, buildTrackingNotification(task));
            sendStatus();
        }
    }

    private void triggerArrival(AppPrefs.ReminderTask task) {
        task.status = AppPrefs.ReminderTask.STATUS_ALERTING;
        task.triggeredAt = System.currentTimeMillis();
        prefs.saveTask(task);
        showArrivalAlert(task);
    }

    private void showArrivalAlert(AppPrefs.ReminderTask task) {
        stopReminderHeartbeat();
        removeLocationUpdates();
        stopArrivalAlarm();
        Notification alarm = ArrivalNotification.build(this, prefs, task);
        try {
            promoteForeground(alarm, true);
        } catch (RuntimeException rejected) {
            stopEverything();
            notifications.notify(ArrivalNotification.ID, alarm);
            return;
        }
        publishRuntime(ReminderRuntime.Snapshot.foreground(
                SystemClock.elapsedRealtime(), SystemClock.elapsedRealtime()));
        alarmTaskId = task.id;
        handler.postDelayed(alarmTimeout, ALARM_DURATION_MS);
        arrivalAlarm.start(() -> {
            AppPrefs.ReminderTask current = prefs.getTask();
            return ownsCurrentRuntime() && current != null && current.isAlerting() && task.id.equals(current.id);
        });
    }

    private void promoteForeground(Notification notification, boolean playingAlarm) {
        int notificationId = playingAlarm ? ArrivalNotification.ID : FOREGROUND_ID;
        if (Build.VERSION.SDK_INT >= 29) {
            int serviceType = playingAlarm ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    : ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            startForeground(notificationId, notification, serviceType);
        } else {
            startForeground(notificationId, notification);
        }
    }

    private void stopArrivalAlarm() {
        handler.removeCallbacks(alarmTimeout);
        alarmTaskId = null;
        arrivalAlarm.stop();
    }

    private Notification buildTrackingNotification(AppPrefs.ReminderTask task) {
        applyLiveDistance(task);
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, ActionReceiver.class).setAction(ActionReceiver.ACTION_STOP_REMINDER);
        PendingIntent stop = PendingIntent.getBroadcast(this, 11, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String detail = Double.isNaN(task.lastDistanceMeters) ? "等待定位 · " + formatRadius(task.radiusMeters) + " 时提醒"
                : "距离约 " + formatDistance(task.lastDistanceMeters) + " · 持续守候中";
        return new Notification.Builder(this, CHANNEL_TRACKING)
                .setSmallIcon(com.example.arrivalalarm.R.drawable.ic_notification)
                .setColor(Color.rgb(0, 122, 255))
                .setContentTitle(prefs.showDestinationInNotifications() ? "正在守候 " + task.destination.name : "到站提醒正在守候")
                .setContentText(prefs.showDestinationInNotifications() ? detail : "持续守候中，可打开应用查看详情")
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(NotificationPrivacy.publicVersion(this, CHANNEL_TRACKING, "到站提醒正在守候"))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(content)
                .addAction(new Notification.Action.Builder(null, "停止", stop).build())
                .build();
    }

    private Notification buildRecoveryNotification(AppPrefs.ReminderTask task, int attempt) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, ActionReceiver.class).setAction(ActionReceiver.ACTION_STOP_REMINDER);
        PendingIntent stop = PendingIntent.getBroadcast(this, 11, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_TRACKING)
                .setSmallIcon(com.example.arrivalalarm.R.drawable.ic_notification)
                .setColor(Color.rgb(0, 122, 255))
                .setContentTitle(prefs.showDestinationInNotifications() ? "正在恢复 " + task.destination.name : "到站提醒正在恢复")
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(NotificationPrivacy.publicVersion(this, CHANNEL_TRACKING, "到站提醒正在恢复"))
                .setContentText("定位暂时中断，正在自动重试 " + attempt + "/" + LocationRecoveryPolicy.MAX_ATTEMPTS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(content)
                .addAction(new Notification.Action.Builder(null, "停止", stop).build())
                .build();
    }

    private void createChannels() {
        NotificationChannel tracking = new NotificationChannel(CHANNEL_TRACKING, "行程监控", NotificationManager.IMPORTANCE_LOW);
        tracking.setDescription("用户主动启动到站提醒后持续显示");
        tracking.setShowBadge(false);
        notifications.createNotificationChannel(tracking);

        ArrivalNotification.createChannel(notifications);
        notifications.deleteNotificationChannel(JourneyPrompt.CHANNEL_ID);
    }

    private void removeLocationUpdates() {
        acceptingLocationCallbacks = false;
        providerRegistry.clear();
        if (locationManager == null) return;
        try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
    }

    private void stopEverything() {
        stopEverything(STOP_FOREGROUND_REMOVE);
    }

    private void stopEverything(int notificationBehavior) {
        resetLocationRecovery();
        stopReminderHeartbeat();
        removeLocationUpdates();
        stopArrivalAlarm();
        stopForeground(notificationBehavior);
        publishRuntime(ReminderRuntime.Snapshot.stopped());
        stopSelf();
    }

    private void startReminderHeartbeat() {
        handler.removeCallbacks(reminderHeartbeat);
        handler.postDelayed(reminderHeartbeat, REMINDER_HEARTBEAT_INTERVAL_MS);
    }

    private void stopReminderHeartbeat() {
        handler.removeCallbacks(reminderHeartbeat);
    }

    private void sendStatus() {
        sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName())
                .putExtra("runtime_state", runtimeSnapshot.state.name())
                .putExtra("registered_provider_count", runtimeSnapshot.registeredProviderCount)
                .putExtra("state_since_elapsed", runtimeSnapshot.stateSinceElapsedRealtime)
                .putExtra("last_heartbeat_elapsed", runtimeSnapshot.lastHeartbeatElapsedRealtime)
                .putExtra("last_location_elapsed", runtimeSnapshot.lastLocationElapsedRealtime)
                .putExtra("recovery_attempt", runtimeSnapshot.recoveryAttempt)
                .putExtra("failure", runtimeSnapshot.failure.name()), PERMISSION_STATUS);
    }

    private void failReminder(ReminderRuntime.Failure failure, String reason) {
        if (!ownsCurrentRuntime()) return;
        AppPrefs.ReminderTask task = prefs.getTask();
        if (task != null && !task.isAlerting()) {
            task.markInterrupted();
            prefs.saveTask(task);
            ReminderRuntimeCoordinator.showRestoreNotification(this, task, reason);
        }
        publishRuntime(ReminderRuntime.Snapshot.failed(failure, SystemClock.elapsedRealtime()));
        stopEverything();
    }

    private void publishRuntime(ReminderRuntime.Snapshot snapshot) {
        if (!ownsCurrentRuntime()) return;
        runtimeSnapshot = snapshot;
        sendStatus();
    }

    private boolean ownsCurrentRuntime() {
        return serviceGeneration == runtimeGeneration;
    }

    private String formatRadius(int meters) { return meters < 1000 ? meters + " 米" : (meters % 1000 == 0 ? String.valueOf(meters / 1000) : String.format(Locale.CHINA, "%.1f", meters / 1000f)) + " 公里"; }
    private String formatDistance(double meters) { return meters < 1000 ? Math.round(meters / 10) * 10 + " 米" : String.format(Locale.CHINA, meters >= 10_000 ? "%.0f 公里" : "%.1f 公里", meters / 1000); }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        prefs.unregisterPreferenceListener(prefsListener);
        resetLocationRecovery();
        stopReminderHeartbeat();
        removeLocationUpdates();
        stopArrivalAlarm();
        if (ownsCurrentRuntime()) {
            distanceTaskId = null;
            runtimeSnapshot = ReminderRuntime.Snapshot.stopped();
            sendStatus();
        }
        super.onDestroy();
    }

    public static void markStartRequested() {
        runtimeGeneration++;
        distanceTaskId = null;
        runtimeSnapshot = ReminderRuntime.Snapshot.starting(SystemClock.elapsedRealtime());
    }

    public static void markStartFailed() {
        runtimeGeneration++;
        runtimeSnapshot = ReminderRuntime.Snapshot.failed(
                ReminderRuntime.Failure.FOREGROUND_START, SystemClock.elapsedRealtime());
    }

    public static ReminderRuntime.Snapshot getRuntimeSnapshot() { return runtimeSnapshot; }

    static void applyLiveDistance(AppPrefs.ReminderTask task) {
        if (task != null && task.id.equals(distanceTaskId)) task.lastDistanceMeters = liveDistanceMeters;
    }
}
