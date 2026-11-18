package com.example.arrivalalarm;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Debug callers only. Accepts diagnostic fields, never Location objects or destinations. */
final class DebugReminderLog {
    private static final String TAG = "DebugReminderLog";
    private static final long MAX_FILE_BYTES = 32 * 1024;
    private static final Executor DISK = Executors.newSingleThreadExecutor();

    enum Journal {
        ARRIVAL("reminder-diagnostics", "device_elapsed_ms,event,provider,accuracy_m,discard_reason\n"),
        PERMISSIONS("permission-diagnostics",
                "device_elapsed_ms,event,detail,precise_location,approximate_location,notification_permission,notifications_enabled\n");

        final String name;
        final String header;

        Journal(String name, String header) {
            this.name = name;
            this.header = header;
        }

        File current(File directory) {
            return new File(directory, name + ".current");
        }

        File previous(File directory) {
            return new File(directory, name + ".previous");
        }
    }

    private DebugReminderLog() {}

    static void begin(Context context) {
        long elapsed = SystemClock.elapsedRealtime();
        onDisk(context, directory -> {
            deleteLogs(directory, Journal.ARRIVAL);
            append(directory, Journal.ARRIVAL, elapsed + ",session_started,,,\n");
        });
    }

    static void fix(Context context, String provider, float accuracy, String discardReason) {
        String accuracyMeters = Float.isFinite(accuracy) && accuracy >= 0 ? Float.toString(accuracy) : "";
        String event = discardReason == null ? "fix_accepted" : "fix_discarded";
        record(context, Journal.ARRIVAL, event + "," + providerLabel(provider) + "," + accuracyMeters + ","
                + (discardReason == null ? "" : discardReason));
    }

    static void provider(Context context, String event, String provider, String reason) {
        record(context, Journal.ARRIVAL, event + "," + providerLabel(provider) + ",," + reason);
    }

    private static String providerLabel(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) return "gps";
        if (LocationManager.NETWORK_PROVIDER.equals(provider)) return "network";
        return "other";
    }

    static void event(Context context, String event) {
        record(context, Journal.ARRIVAL, event + ",,,");
    }

    static void permission(Context context, String event, String detail) {
        boolean precise = ReminderPermissions.hasLocation(context);
        boolean approximate = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        String notification = Build.VERSION.SDK_INT >= 33
                ? Boolean.toString(ReminderPermissions.hasNotifications(context)) : "not_applicable";
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        String notificationsEnabled = manager == null ? "unknown" : Boolean.toString(manager.areNotificationsEnabled());
        record(context, Journal.PERMISSIONS, event + "," + detail + "," + precise + "," + approximate
                + "," + notification + "," + notificationsEnabled);
    }

    static void clear(Context context) {
        onDisk(context, directory -> {
            for (Journal journal : Journal.values()) deleteLogs(directory, journal);
        });
    }

    static void export(Context context, Uri destination, Journal journal) {
        Context app = context.getApplicationContext();
        long exportElapsed = SystemClock.elapsedRealtime();
        DISK.execute(() -> {
            byte[] snapshot;
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                bytes.write(journal.header.getBytes(StandardCharsets.UTF_8));
                File directory = app.getNoBackupFilesDir();
                for (File file : new File[]{journal.previous(directory), journal.current(directory)}) {
                    if (file.isFile()) Files.copy(file.toPath(), bytes);
                }
                if (journal == Journal.ARRIVAL && bytes.size() > journal.header.length()) {
                    // Anchor the observation window even when the provider sent no more events.
                    bytes.write((exportElapsed + ",log_exported,,,\n").getBytes(StandardCharsets.UTF_8));
                }
                snapshot = bytes.toByteArray();
            } catch (IOException | RuntimeException failed) {
                toast(app, "无法读取调试日志");
                return;
            }
            // The selected document provider may be slow. Keep the log writer free.
            new Thread(() -> {
                try (OutputStream output = app.getContentResolver().openOutputStream(destination, "wt")) {
                    if (output == null) throw new IOException("Document unavailable");
                    output.write(snapshot);
                } catch (IOException | RuntimeException failed) {
                    toast(app, "调试日志导出失败");
                    return;
                }
                toast(app, snapshot.length == journal.header.length() ? "已导出，暂无诊断事件" : "调试日志已导出");
            }, "ReminderLogExport").start();
        });
    }

    private static void toast(Context app, String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app, message, Toast.LENGTH_SHORT).show());
    }

    private static void record(Context context, Journal journal, String fields) {
        String line = SystemClock.elapsedRealtime() + "," + fields + "\n";
        onDisk(context, directory -> append(directory, journal, line));
    }

    private static void onDisk(Context context, DiskOperation operation) {
        Context app = context.getApplicationContext();
        DISK.execute(() -> {
            try {
                operation.run(app.getNoBackupFilesDir());
            } catch (IOException | RuntimeException failed) {
                Log.w(TAG, "Could not write reminder diagnostics");
            }
        });
    }

    private static void append(File directory, Journal journal, String line) throws IOException {
        File current = journal.current(directory);
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (current.length() + bytes.length > MAX_FILE_BYTES
                && !current.renameTo(journal.previous(directory))) {
            throw new IOException("Could not rotate reminder diagnostics");
        }
        try (FileOutputStream output = new FileOutputStream(current, true)) {
            output.write(bytes);
        }
    }

    private static void deleteLogs(File directory, Journal journal) throws IOException {
        Files.deleteIfExists(journal.previous(directory).toPath());
        Files.deleteIfExists(journal.current(directory).toPath());
    }

    private interface DiskOperation {
        void run(File directory) throws IOException;
    }
}
