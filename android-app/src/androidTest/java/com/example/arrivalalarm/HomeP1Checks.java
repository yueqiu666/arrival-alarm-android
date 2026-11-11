package com.example.arrivalalarm;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.inspector.WindowInspector;
import android.widget.Button;
import android.widget.EditText;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

final class HomeP1Checks {
    private static final AppPrefs.Destination PLACE = new AppPrefs.Destination(
            "home-test", "测试车站", "测试路", "测试市", 30.6, 104.1);

    private HomeP1Checks() { }

    static void run(Instrumentation instrumentation, boolean expectNight) throws Exception {
        Context target = instrumentation.getTargetContext();
        verifyStorage(target);
        MainActivity first = (MainActivity) instrumentation.startActivitySync(new Intent(target, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        onMain(instrumentation, () -> {
            EditText input = input(first);
            input.setText("站");
            input.setSelection(0);
            call(first, "showDestinationConfirmation",
                    new Class<?>[]{AppPrefs.Destination.class, String.class, List.class}, PLACE, "核对目的地", null);
            return null;
        });
        MainActivity second = recreate(instrumentation, first);
        onMain(instrumentation, () -> {
            check("站".contentEquals(input(second).getText()) && input(second).getSelectionEnd() == 0,
                    "draft or selection was not restored");
            check(button("确认使用") != null, "destination confirmation was not restored");
            check(new AppPrefs(second).getTask() == null, "recreation started a reminder without confirmation");
            check(button("取消").performClick(), "confirmation could not be cancelled");
            return null;
        });
        instrumentation.waitForIdleSync();
        onMain(instrumentation, () -> {
            call(second, "requestStart", new Class<?>[]{AppPrefs.Destination.class}, PLACE);
            check(button("继续") != null, "start should require permission in this fixture");
            return null;
        });
        MainActivity third = recreate(instrumentation, second);
        onMain(instrumentation, () -> {
            check(button("继续") != null, "permission rationale was not restored");
            AppPrefs.Destination pending = (AppPrefs.Destination) field(third, "pendingDestination");
            check(pending != null && PLACE.id.equals(pending.id), "pending permission lost its destination");
            check(button("暂不").performClick(), "rationale could not be cancelled");
            View scroll = third.findViewById(android.R.id.content);
            if (expectNight) {
                check((third.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES, "night mode was not applied");
                View content = ((android.view.ViewGroup) scroll).getChildAt(0);
                check(((ColorDrawable) content.getBackground()).getColor() == 0xFF121419, "night palette missing");
            }
            return null;
        });
        instrumentation.waitForIdleSync();
        onMain(instrumentation, () -> {
            check(field(third, "pendingDestination") == null, "cancelled permission still holds a destination");
            third.finish();
            return null;
        });
    }

    private static void verifyStorage(Context target) throws Exception {
        Context isolated = new ContextWrapper(target) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("p1_storage_check", mode);
            }
        };
        SharedPreferences raw = isolated.getSharedPreferences("p1_storage_check", Context.MODE_PRIVATE);
        AppPrefs prefs = new AppPrefs(isolated);
        prefs.clearAll();
        try {
            AppPrefs.ReminderTask old = new AppPrefs.ReminderTask("old", PLACE, 1000, "active", 1);
            raw.edit().putString("task", old.toJson().put("lastDistanceMeters", 456).toString())
                    .putString("recents", "[null," + PLACE.toJson() + "]").apply();
            AppPrefs.ReminderTask loaded = prefs.getTask();
            check(loaded != null && "old".equals(loaded.id) && Double.isNaN(loaded.lastDistanceMeters),
                    "legacy task or live distance migration failed");
            prefs.saveTask(loaded);
            check(raw.getString("task", "").contains("\"schemaVersion\":1")
                    && !raw.getString("task", "").contains("lastDistanceMeters"), "current task record has the wrong shape");
            check(prefs.getRecents().size() == 1 && prefs.hasUnreadableData()
                    && raw.getString("quarantine_recents", "").startsWith("[null,"), "list damage was not salvaged");
            raw.edit().putString("task", "{broken").apply();
            check(prefs.getTask() == null && "{broken".equals(raw.getString("quarantine_task", "")),
                    "invalid task was not quarantined");
            prefs.clearAll();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread a = writer(isolated, "a", failure);
            Thread b = writer(isolated, "b", failure);
            a.start();
            b.start();
            a.join();
            b.join();
            if (failure.get() != null) throw new AssertionError(failure.get());
            check(prefs.getRecents().size() == 2 && !prefs.hasUnreadableData(), "concurrent writes or clear failed");
        } finally {
            prefs.clearAll();
        }
    }

    private static Thread writer(Context context, String id, AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                AppPrefs prefs = new AppPrefs(context);
                for (int i = 0; i < 20; i++) prefs.addRecent(new AppPrefs.Destination(id, id, "", "", 0, 0));
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        });
    }

    private static MainActivity recreate(Instrumentation instrumentation, MainActivity current) throws Exception {
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
        try {
            onMain(instrumentation, () -> { current.recreate(); return null; });
            Activity replacement = instrumentation.waitForMonitorWithTimeout(monitor, 8000L);
            check(replacement != null && replacement != current, "activity did not recreate");
            instrumentation.waitForIdleSync();
            return (MainActivity) replacement;
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    private static Button button(String label) {
        for (View window : WindowInspector.getGlobalWindowViews()) {
            for (int id : new int[]{android.R.id.button1, android.R.id.button2}) {
                View view = window.findViewById(id);
                if (view instanceof Button && view.isShown() && label.contentEquals(((Button) view).getText())) {
                    return (Button) view;
                }
            }
        }
        return null;
    }

    private static EditText input(MainActivity activity) throws Exception {
        return (EditText) field(activity, "destinationInput");
    }

    private static Object field(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static void call(MainActivity activity, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(activity, arguments);
    }

    private static <T> T onMain(Instrumentation instrumentation, java.util.concurrent.Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        instrumentation.runOnMainSync(task);
        return task.get();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
