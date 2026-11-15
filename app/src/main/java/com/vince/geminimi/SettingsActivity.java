package com.vince.geminimi;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.util.Locale;

public final class SettingsActivity extends Activity {
    private static final int INK = Color.rgb(26, 48, 36);
    private static final int GREEN = Color.rgb(43, 98, 72);
    private static final int RED = 0xFFA32932;
    private static final int CARD_BACKGROUND = 0xFFDCE9DE;
    private static final int MUTED = Color.rgb(74, 96, 83);
    private int savedMs;
    private TextView diagnosticText;
    private TextView lockScreenStatus;
    private Button refreshButton;
    private boolean diagnosticsRefreshing;
    private boolean hasDiagnostics;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ContentObserver diagnosticObserver = new ContentObserver(mainHandler) {
        @Override public void onChange(boolean selfChange) {
            Bundle snapshot = getContentResolver().call(
                    Diagnostics.RESULT, Diagnostics.READ, null, null);
            if (snapshot != null) {
                mainHandler.removeCallbacks(diagnosticTimeout);
                diagnosticsRefreshing = false;
                hasDiagnostics = true;
                refreshButton.setText("刷新自检");
                showDiagnostics(snapshot);
            }
        }
    };
    private final Runnable diagnosticTimeout = () -> {
        diagnosticsRefreshing = false;
        refreshButton.setText("自检超时，点击重试");
        if (!hasDiagnostics) showDiagnostics(null);
    };

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

        LinearLayout card = card(page, "长按唤醒时间", 24);
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

        Button save = actionButton("保存", true);
        add(card, save, 18);

        Button reset = actionButton("恢复默认 0.5 秒", false);
        add(card, reset, 8);

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

        LinearLayout diagnostics = card(page, "自检", 14);
        diagnosticText = label("", 14, MUTED, false);
        diagnosticText.setLineSpacing(dp(4), 1f);
        diagnosticText.setMinLines(5);
        diagnosticText.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(diagnostics, diagnosticText, 8);
        refreshButton = actionButton("刷新自检", true);
        refreshButton.setOnClickListener(view -> refreshDiagnostics());
        add(diagnostics, refreshButton, 18);

        LinearLayout lockScreen = card(page, "锁屏唤起", 14);
        lockScreenStatus = label("", 14, MUTED, false);
        lockScreenStatus.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(lockScreen, lockScreenStatus, 8);
        add(lockScreen, label("在 Google App 的其他权限中允许「在锁屏上显示」，"
                + "并在 Gemini 设置中开启「无需解锁即可使用 Gemini」。", 14, MUTED, false), 6);
        Button permissions = actionButton("打开 Google 权限设置", true);
        permissions.setOnClickListener(view -> openGooglePermissions());
        add(lockScreen, permissions, 18);

        LinearLayout screen = card(page, "屏幕内容", 14);
        Switch shareScreen = new Switch(this);
        shareScreen.setText("提供屏幕内容");
        shareScreen.setTextColor(INK);
        shareScreen.setMinHeight(dp(48));
        shareScreen.setChecked(TimingSettings.screenContextEnabled(this));
        TextView screenSummary = label(screenContextSummary(shareScreen.isChecked()), 14, MUTED, false);
        screenSummary.setMinLines(2);
        screenSummary.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        shareScreen.setOnClickListener(view -> {
            boolean enabled = shareScreen.isChecked();
            if (!TimingSettings.saveScreenContext(this, enabled)) {
                shareScreen.setChecked(!enabled);
                Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
            }
            screenSummary.setText(screenContextSummary(shareScreen.isChecked()));
        });
        add(screen, shareScreen, 8);
        add(screen, screenSummary, 4);

        LinearLayout info = card(page, "电源菜单", 14);
        add(info, label("持续按住 5 秒可打开系统电源菜单。", 14, MUTED, false), 6);
    }

    @Override
    protected void onStart() {
        super.onStart();
        getContentResolver().registerContentObserver(Diagnostics.RESULT, false, diagnosticObserver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDiagnostics();
    }

    @Override
    protected void onStop() {
        getContentResolver().unregisterContentObserver(diagnosticObserver);
        mainHandler.removeCallbacks(diagnosticTimeout);
        diagnosticsRefreshing = false;
        super.onStop();
    }

    private void refreshDiagnostics() {
        if (diagnosticsRefreshing) return;
        diagnosticsRefreshing = true;
        refreshButton.setText("正在自检…");
        if (!hasDiagnostics) {
            diagnosticText.setText(defaultAssistantStatus() + "\n正在检查系统模块…");
            lockScreenStatus.setText("Google 锁屏显示 · 正在检查…");
        }
        mainHandler.postDelayed(diagnosticTimeout, 2500);
        getContentResolver().notifyChange(Diagnostics.REQUEST, null, 0);
    }

    private String defaultAssistantStatus() {
        try {
            String value = Settings.Secure.getString(getContentResolver(), Constants.SECURE_ASSISTANT);
            ComponentName component = value == null ? null : ComponentName.unflattenFromString(value);
            return "默认助手设置 · " + assistantName(component == null ? "" : component.getPackageName());
        } catch (SecurityException e) {
            return "默认助手设置 · 无法读取";
        }
    }

    private void showDiagnostics(Bundle snapshot) {
        int permission = snapshot == null ? Diagnostics.LOCK_SCREEN_UNKNOWN : snapshot.getInt(
                Diagnostics.LOCK_SCREEN_PERMISSION, Diagnostics.LOCK_SCREEN_UNKNOWN);
        String permissionLabel = switch (permission) {
            case Diagnostics.LOCK_SCREEN_ALLOWED -> "已允许";
            case Diagnostics.LOCK_SCREEN_DENIED -> "未允许";
            default -> "无法确认，请在权限页查看";
        };
        int permissionColor = switch (permission) {
            case Diagnostics.LOCK_SCREEN_ALLOWED -> GREEN;
            case Diagnostics.LOCK_SCREEN_DENIED -> RED;
            default -> MUTED;
        };
        SpannableStringBuilder permissionText = new SpannableStringBuilder("Google 锁屏显示 · ");
        appendStatus(permissionText, permissionLabel, permissionColor);
        lockScreenStatus.setText(permissionText);
        String assistant = defaultAssistantStatus();
        if (snapshot == null) {
            diagnosticText.setText(assistant + "\n系统模块 · 未收到响应，请确认系统框架作用域并重启");
            return;
        }
        long keyAt = snapshot.getLong(Diagnostics.LAST_KEY);
        long sessionAt = snapshot.getLong(Diagnostics.LAST_SESSION);
        boolean keyHookInstalled = snapshot.getBoolean(Diagnostics.KEY_HOOK);
        String result = switch (snapshot.getInt(Diagnostics.SESSION_RESULT)) {
            case Diagnostics.VOICE_ACCEPTED -> "语音服务已接受";
            case Diagnostics.SYSTEM_UI_REQUESTED -> "已交给系统界面";
            default -> "请求失败";
        };
        String lockScreen = snapshot.getBoolean(Diagnostics.SESSION_LOCKED) ? "（锁屏）" : "";
        SpannableStringBuilder report = new SpannableStringBuilder(assistant)
                .append("\n当前语音服务 · ")
                .append(assistantName(snapshot.getString(Diagnostics.VOICE_SERVICE)))
                .append("\n电源键监听 · ");
        appendStatus(report, keyHookInstalled ? "已挂钩" : "未挂钩",
                keyHookInstalled ? GREEN : RED);
        report.append("\n最近按键 · ").append(keyAt == 0 ? "本次开机未检测到" : formatTime(keyAt))
                .append("\n最近长按 · ").append(sessionAt == 0 ? "本次开机无记录"
                        : result + lockScreen + "，" + formatTime(sessionAt));
        diagnosticText.setText(report);
    }

    private static void appendStatus(SpannableStringBuilder text, String status, int color) {
        int start = text.length();
        text.append(status);
        text.setSpan(new ForegroundColorSpan(color), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void openGooglePermissions() {
        Intent editor = new Intent("miui.intent.action.APP_PERM_EDITOR")
                .setPackage("com.miui.securitycenter")
                .putExtra("extra_pkgname", Constants.GOOGLE_APP_PKG);
        try {
            startActivity(editor);
        } catch (ActivityNotFoundException | SecurityException e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", Constants.GOOGLE_APP_PKG, null)));
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(this, "请在系统设置中打开 Google App 的应用信息",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private static String assistantName(String packageName) {
        if (packageName == null) return "未知";
        if (packageName.isEmpty()) return "未设置";
        if (Constants.GOOGLE_APP_PKG.equals(packageName)) return "Google";
        if (Constants.XIAOAI_PKG.equals(packageName)) return "小爱同学";
        return packageName;
    }

    private String formatTime(long millis) {
        return DateFormat.getTimeFormat(this).format(new Date(millis));
    }

    private static String screenContextSummary(boolean enabled) {
        return enabled
                ? "已开启。长按电源键唤起 Gemini 时，会请求屏幕文字和截图。"
                : "已关闭。长按电源键唤起 Gemini 时，不会采集屏幕文字或截图。";
    }

    private int selectedMs(SeekBar slider) {
        return TimingSettings.MIN_MS + slider.getProgress() * TimingSettings.STEP_MS;
    }

    private String seconds(int millis) {
        return String.format(Locale.SIMPLIFIED_CHINESE, "%.1f 秒", millis / 1000.0);
    }

    private Button actionButton(String text, boolean primary) {
        Button button = new Button(this, null, android.R.attr.borderlessButtonStyle);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : GREEN);
        button.setMinHeight(dp(48));
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable background = shape(primary ? GREEN : Color.TRANSPARENT, 24);
        if (!primary) background.setStroke(dp(1), GREEN);
        int rippleColor = primary ? 0x33FFFFFF : 0x222B6248;
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor),
                background, shape(Color.WHITE, 24)));
        return button;
    }

    private LinearLayout card(LinearLayout parent, String title, int top) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(shape(CARD_BACKGROUND, 24));
        card.addView(label(title, 20, INK, true));
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
