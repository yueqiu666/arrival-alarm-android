package com.example.arrivalalarm;

import android.app.Notification;
import android.content.Context;

/** Applies the same redacted lockscreen version to every reminder notification. */
final class NotificationPrivacy {
    private NotificationPrivacy() {}

    static Notification publicVersion(Context context, String channel, String title) {
        return new Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
    }
}
