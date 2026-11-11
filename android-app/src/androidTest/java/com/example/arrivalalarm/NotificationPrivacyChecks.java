package com.example.arrivalalarm;

import android.app.Notification;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

/** Device checks use isolated preferences and never clear user data. */
final class NotificationPrivacyChecks {
    static void run(Context context) {
        Context isolated = new ContextWrapper(context) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("notification_privacy_test", mode);
            }
        };
        AppPrefs prefs = new AppPrefs(isolated);
        prefs.clearAll();
        try {
            check(!prefs.showDestinationInNotifications(), "default must hide destination");
            prefs.setShowDestinationInNotifications(true);
            check(new AppPrefs(isolated).showDestinationInNotifications(), "opt-in must persist");
            prefs.setShowDestinationInNotifications(false);
            check(!new AppPrefs(isolated).showDestinationInNotifications(), "opt-out must persist");
            prefs.setShowDestinationInNotifications(true);
            prefs.clearAll();
            check(!prefs.showDestinationInNotifications(), "clear must restore privacy default");
            Notification notification = NotificationPrivacy.publicVersion(context, "arrival_alarm", "到站提醒已触发");
            check("到站提醒已触发".contentEquals(notification.extras.getCharSequence(Notification.EXTRA_TITLE)), "public title");
            check(notification.extras.getCharSequence(Notification.EXTRA_TEXT) == null, "no public details");
            check(notification.actions == null || notification.actions.length == 0, "no public actions");
            check(notification.visibility == Notification.VISIBILITY_PUBLIC, "public visibility");
        } finally {
            prefs.clearAll();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
