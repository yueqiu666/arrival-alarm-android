package com.example.arrivalalarm;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Owns the alarm output. Called on the tracking service's main thread. */
final class ArrivalAlarm {
    private static final String TAG = "ArrivalAlarm";
    private static final AudioAttributes ALARM_AUDIO = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    private final Context context;
    private MediaPlayer player;
    private Vibrator vibrator;

    ArrivalAlarm(Context context) {
        this.context = context;
    }

    void start(BooleanSupplier stillCurrent) {
        vibrate();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(ALARM_AUDIO);
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            player.setOnPreparedListener(prepared -> {
                if (player != prepared) return;
                if (!stillCurrent.getAsBoolean()) {
                    releasePlayer();
                    return;
                }
                try {
                    prepared.setLooping(true);
                    prepared.start();
                } catch (IllegalStateException failed) {
                    releasePlayer();
                    Log.w(TAG, "Could not start arrival alarm audio");
                }
            });
            player.setOnErrorListener((failedPlayer, what, extra) -> {
                if (player == failedPlayer) releasePlayer();
                Log.w(TAG, "Arrival alarm audio error " + what + "/" + extra);
                return true;
            });
            player.prepareAsync();
        } catch (IOException | RuntimeException failed) {
            releasePlayer();
            Log.w(TAG, "Could not open system alarm tone");
        }
    }

    @SuppressWarnings("deprecation")
    private void vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager manager = context.getSystemService(VibratorManager.class);
                vibrator = manager == null ? null : manager.getDefaultVibrator();
            } else {
                vibrator = context.getSystemService(Vibrator.class);
            }
            if (vibrator == null || !vibrator.hasVibrator()) return;
            // Ten bursts spaced 30 seconds apart. The effect also ends if audio is unavailable.
            long[] interval = {0, 500, 180, 500, 180, 900, 27_740, 0};
            long[] timings = new long[interval.length * 10];
            for (int offset = 0; offset < timings.length; offset += interval.length) {
                System.arraycopy(interval, 0, timings, offset, interval.length);
            }
            VibrationEffect effect = VibrationEffect.createWaveform(timings, -1);
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
            } else {
                vibrator.vibrate(effect, ALARM_AUDIO);
            }
        } catch (RuntimeException failed) {
            vibrator = null;
            Log.w(TAG, "Could not vibrate for arrival alarm");
        }
    }

    void stop() {
        releasePlayer();
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    private void releasePlayer() {
        if (player == null) return;
        MediaPlayer previous = player;
        player = null;
        previous.release();
    }
}
