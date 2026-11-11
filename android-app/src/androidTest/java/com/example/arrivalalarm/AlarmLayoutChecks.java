package com.example.arrivalalarm;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.concurrent.FutureTask;

final class AlarmLayoutChecks {
    private AlarmLayoutChecks() { }

    static void run(Instrumentation instrumentation) throws Exception {
        Context context = instrumentation.getTargetContext();
        AppPrefs prefs = new AppPrefs(context);
        check(prefs.getTask() == null, "the isolated app must start without a task");
        AppPrefs.Destination destination = new AppPrefs.Destination("alarm-layout-fixture",
                "成都地铁测试站东侧交通换乘中心公共交通枢纽地铁站第一入口无障碍电梯候车区测试目的地",
                "", "", 30.657, 104.065);
        prefs.saveTask(new AppPrefs.ReminderTask("alarm-layout-fixture", destination, 1000,
                AppPrefs.ReminderTask.STATUS_ALERTING, System.currentTimeMillis()));
        Activity activity = null;
        try {
            activity = instrumentation.startActivitySync(new Intent(context, AlarmActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();
            Activity opened = activity;
            FutureTask<Void> layoutCheck = new FutureTask<>(() -> {
                checkArrivalAction(opened);
                return null;
            });
            instrumentation.runOnMainSync(layoutCheck);
            layoutCheck.get();
            check(prefs.getTask() == null, "the arrival action did not clear the task");
        } finally {
            if (activity != null) instrumentation.runOnMainSync(activity::finish);
            prefs.clearTask();
        }
    }

    private static void checkArrivalAction(Activity activity) {
        Configuration configuration = activity.getResources().getConfiguration();
        check(configuration.fontScale >= 2f && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                "set the emulator to landscape and 200% font size before running this check");
        ViewGroup content = activity.findViewById(android.R.id.content);
        ArrayList<View> actions = new ArrayList<>();
        content.findViewsWithText(actions, "我已到达", View.FIND_VIEWS_WITH_TEXT);
        check(actions.size() == 1, "expected one arrival action");
        View action = actions.get(0);
        if (content.getChildAt(0) instanceof ScrollView) {
            ScrollView scroll = (ScrollView) content.getChildAt(0);
            scroll.setSmoothScrollingEnabled(false);
            scroll.fullScroll(View.FOCUS_DOWN);
        }
        Rect visible = new Rect();
        check(action.getLocalVisibleRect(visible) && visible.width() == action.getWidth()
                && visible.height() == action.getHeight(), "arrival action is clipped and cannot be fully reached");
        WindowInsets insets = content.getRootWindowInsets();
        Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Rect safe = new Rect(activity.getWindowManager().getCurrentWindowMetrics().getBounds());
        safe.set(safe.left + bars.left, safe.top + bars.top, safe.right - bars.right, safe.bottom - bars.bottom);
        int[] location = new int[2];
        action.getLocationOnScreen(location);
        check(safe.contains(location[0], location[1], location[0] + action.getWidth(), location[1] + action.getHeight()),
                "arrival action overlaps a system bar or display cutout");
        check(action.performClick(), "arrival action is not clickable");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
