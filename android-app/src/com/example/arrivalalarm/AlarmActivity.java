package com.example.arrivalalarm;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Objects;

public final class AlarmActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Destination details are shown only after the user unlocks the device.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AppPrefs.ReminderTask task = new AppPrefs(this).getTask();
        String taskId = getIntent().getStringExtra(ArrivalNotification.EXTRA_TASK_ID);
        if (task == null || !task.isAlerting() || (taskId != null && !task.id.equals(taskId))) {
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.paper));
        if (Build.VERSION.SDK_INT >= 35) {
            // The platform fits the window on older versions. Edge-to-edge needs explicit padding.
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(40), dp(28), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView icon = text(getString(R.string.arrival_icon), 38, Typeface.BOLD, getColor(R.color.deep_green));
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(getColor(R.color.soft_blue), 28));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(icon, new LinearLayout.LayoutParams(dp(92), dp(92)));

        TextView eyebrow = text(getString(R.string.arrival_near), 13, Typeface.BOLD, getColor(R.color.deep_green));
        LinearLayout.LayoutParams eyebrowParams = wrap();
        eyebrowParams.topMargin = dp(28);
        root.addView(eyebrow, eyebrowParams);

        TextView title = text(getString(R.string.arrival_title, task.destination.name), 32, Typeface.BOLD, getColor(R.color.ink));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(12);
        root.addView(title, titleParams);

        TextView detail = text(getString(R.string.arrival_details), 16, Typeface.NORMAL, getColor(R.color.muted));
        LinearLayout.LayoutParams detailParams = wrap();
        detailParams.topMargin = dp(12);
        detailParams.bottomMargin = dp(30);
        root.addView(detail, detailParams);

        Button arrived = new Button(this);
        arrived.setText(getString(R.string.arrival_acknowledge));
        arrived.setTextColor(getColor(R.color.on_primary_button));
        arrived.setTextSize(18);
        arrived.setMinHeight(getResources().getDimensionPixelSize(R.dimen.alarm_button_min_height));
        arrived.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        arrived.setStateListAnimator(null);
        arrived.setBackground(roundRect(getColor(R.color.primary_button), 16));
        arrived.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                else view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                ReminderRuntimeCoordinator.acknowledgeArrival(AlarmActivity.this, task.id);
                finish();
            }
        });
        attachPressFeedback(arrived);
        root.addView(arrived, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Objects.equals(getIntent().getStringExtra(ArrivalNotification.EXTRA_TASK_ID),
                intent.getStringExtra(ArrivalNotification.EXTRA_TASK_ID))) return;
        setIntent(intent);
        recreate();
    }

    // 该监听只做按压缩放动画并始终返回 false，点击仍由按钮自身处理，因此无需再调用 performClick。
    @SuppressLint("ClickableViewAccessibility")
    private void attachPressFeedback(final Button button) {
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start();
                else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
                }
                return false;
            }
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private TextView text(String value, int sp, int style, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color); view.setTypeface(Typeface.create("sans", style)); return view; }
    private GradientDrawable roundRect(int color, int radiusDp) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radiusDp)); return drawable; }
}
