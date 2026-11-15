package com.vince.geminimi;

import android.net.Uri;

public final class Diagnostics {
    public static final Uri REQUEST = TimingSettings.URI.buildUpon().path("diagnostics/request").build();
    public static final Uri RESULT = TimingSettings.URI.buildUpon().path("diagnostics/result").build();
    public static final String READ = "read_diagnostics";
    public static final String REPORT = "report_diagnostics";
    public static final String KEY_HOOK = "key_hook";
    public static final String LAST_KEY = "last_key";
    public static final String LAST_SESSION = "last_session";
    public static final String SESSION_LOCKED = "session_locked";
    public static final String SESSION_RESULT = "session_result";
    public static final String LOCK_SCREEN_PERMISSION = "lock_screen_permission";
    public static final int LOCK_SCREEN_UNKNOWN = -1;
    public static final int LOCK_SCREEN_DENIED = 0;
    public static final int LOCK_SCREEN_ALLOWED = 1;
    public static final String VOICE_SERVICE = "voice_service";
    public static final int FAILED = 0;
    public static final int VOICE_ACCEPTED = 1;
    public static final int SYSTEM_UI_REQUESTED = 2;

    private Diagnostics() {}
}
