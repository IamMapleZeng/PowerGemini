package com.vince.geminimi;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public final class TimingSettings {
    public static final int MIN_MS = 300;
    public static final int MAX_MS = 3000;
    public static final int STEP_MS = 100;
    public static final int DEFAULT_MS = 500;
    public static final Uri URI = Uri.parse("content://io.github.powergemini.timing/delay");
    private static final String KEY = "delay_ms";

    private TimingSettings() {}

    public static int sanitize(int millis) {
        return millis >= MIN_MS && millis <= MAX_MS && millis % STEP_MS == 0
                ? millis : DEFAULT_MS;
    }

    public static int read(Context context) {
        return sanitize(preferences(context).getInt(KEY, DEFAULT_MS));
    }

    public static boolean save(Context context, int millis) {
        if (!preferences(context).edit().putInt(KEY, sanitize(millis)).commit()) return false;
        context.getContentResolver().notifyChange(URI, null, 0);
        return true;
    }

    private static SharedPreferences preferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences("power_timing", Context.MODE_PRIVATE);
    }
}
