package com.example.arrivalalarm;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

final class ReminderPermissions {
    private ReminderPermissions() { }

    static boolean hasLocation(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean hasNotifications(Context context) {
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static String[] missing(Context context) {
        List<String> permissions = new ArrayList<>();
        if (!hasLocation(context)) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!hasNotifications(context)) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        return permissions.toArray(new String[0]);
    }
}
