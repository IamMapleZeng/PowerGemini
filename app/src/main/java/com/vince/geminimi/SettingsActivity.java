package com.vince.geminimi;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public final class SettingsActivity extends Activity {
    private static final int INK = Color.rgb(26, 48, 36);
    private static final int GREEN = Color.rgb(43, 98, 72);
    private static final int MUTED = Color.rgb(74, 96, 83);
    private int savedMs;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        savedMs = TimingSettings.read(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(24), dp(22), dp(24));
        scroll.addView(page);
        setContentView(scroll);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_power_gemini);
        heading.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView title = label("Power Gemini", 26, INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.setMarginStart(dp(12));
        heading.addView(title, titleParams);
        page.addView(heading);

        LinearLayout card = card(page, 0xFFDCE9DE, 24);
        card.addView(label("长按唤醒时间", 20, INK, true));
        add(card, label("按住电源键达到设定时间后，弹出 Gemini。", 14, MUTED, false), 8);

        TextView value = label("", 40, GREEN, true);
        value.setGravity(Gravity.CENTER);
        add(card, value, 14);

        SeekBar slider = new SeekBar(this);
        slider.setId(100);
        slider.setMax((TimingSettings.MAX_MS - TimingSettings.MIN_MS) / TimingSettings.STEP_MS);
        slider.setProgress((savedMs - TimingSettings.MIN_MS) / TimingSettings.STEP_MS);
        slider.setContentDescription("长按唤醒时间");
        slider.setProgressTintList(ColorStateList.valueOf(GREEN));
        slider.setThumbTintList(ColorStateList.valueOf(GREEN));
        add(card, slider, 4);

        LinearLayout limits = new LinearLayout(this);
        limits.addView(label(seconds(TimingSettings.MIN_MS), 13, MUTED, false),
                new LinearLayout.LayoutParams(0, -2, 1));
        limits.addView(label(seconds(TimingSettings.MAX_MS), 13, MUTED, false));
        card.addView(limits);

        Button save = new Button(this);
        save.setText("保存");
        save.setTextColor(Color.WHITE);
        save.setBackground(shape(GREEN, 24));
        save.setMinHeight(dp(48));
        add(card, save, 18);

        Button reset = new Button(this, null, android.R.attr.borderlessButtonStyle);
        reset.setText("恢复默认 0.5 秒");
        reset.setTextColor(GREEN);
        add(card, reset, 4);

        TextView feedback = label("当前设置 " + seconds(savedMs), 14, MUTED, false);
        feedback.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(card, feedback, 6);
        value.setText(seconds(savedMs));

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText(seconds(selectedMs(bar)));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        save.setOnClickListener(view -> {
            int selected = selectedMs(slider);
            if (TimingSettings.save(this, selected)) {
                savedMs = selected;
                feedback.setText("已保存 " + seconds(savedMs));
            } else {
                feedback.setText("保存失败，请重试");
            }
        });
        reset.setOnClickListener(view -> slider.setProgress(
                (TimingSettings.DEFAULT_MS - TimingSettings.MIN_MS) / TimingSettings.STEP_MS));

        LinearLayout info = card(page, 0xFFEAF1E9, 14);
        info.addView(label("电源菜单", 16, INK, true));
        add(info, label("持续按住 5 秒可打开系统电源菜单。", 14, MUTED, false), 6);
    }

    private int selectedMs(SeekBar slider) {
        return TimingSettings.MIN_MS + slider.getProgress() * TimingSettings.STEP_MS;
    }

    private String seconds(int millis) {
        return String.format(Locale.SIMPLIFIED_CHINESE, "%.1f 秒", millis / 1000.0);
    }

    private LinearLayout card(LinearLayout parent, int color, int top) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(shape(color, 24));
        add(parent, card, top);
        return card;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void add(LinearLayout parent, android.view.View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(child, params);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
