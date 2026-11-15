package com.vince.geminimi.hooks;

import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.vince.geminimi.Constants;
import com.vince.geminimi.Diagnostics;
import com.vince.geminimi.TimingSettings;

import android.database.ContentObserver;
import android.database.Cursor;
import android.os.HandlerThread;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 接管长按电源键，发送 ACTION_ASSIST，由系统路由到当前数字助理 (Gemini) 的 Overlay。
 * 国行 HyperOS 通常在 PhoneWindowManager 的 launchAssistAction / launchVoiceAssist
 * 或 MIUI 自定义的 launchSuperXiaoAi 上做分支。我们覆盖最常见的几个签名。
 */
public final class PowerKeyOverlayHook {

    private PowerKeyOverlayHook() {}

    private static final String PWM = "com.android.server.policy.PhoneWindowManager";
    private static final String MIUI_SHORTCUT_ACTIONS =
            "com.miui.server.input.util.ShortCutActionsUtils";
    // HyperOS uses this AppOp to allow an app's activity to appear over keyguard.
    private static final int MIUI_OP_SHOW_WHEN_LOCKED = 10020;
    private static final int SHOW_WITH_ASSIST = 1;
    private static final int SHOW_WITH_SCREENSHOT = 1 << 1;
    private static final int SHOW_SOURCE_PUSH_TO_TALK = 1 << 4;
    private static final int SCREEN_CONTEXT_FLAGS = SHOW_WITH_ASSIST | SHOW_WITH_SCREENSHOT;
    private static final ThreadLocal<boolean[]> PRIVATE_SESSION = new ThreadLocal<>();
    private static boolean sContextHookInstalled;
    private static volatile boolean sScreenContextEnabled = true;
    private static final Object SEQUENCE_LOCK = new Object();
    private static final Handler SEQUENCE_HANDLER = new Handler(Looper.getMainLooper());
    private static Object sPhoneWindowManager;
    private static long sPowerDownAt = -1L;
    private static Runnable sGeminiRunnable;
    private static Runnable sPowerMenuRunnable;
    private static volatile int sConfiguredDelayMs = TimingSettings.DEFAULT_MS;
    private static int sPressDelayMs = TimingSettings.DEFAULT_MS;
    private static Handler sSettingsHandler;
    private static boolean sAssistantRequested;
    private static boolean sAssistantAccepted;
    private static volatile boolean sKeyHookInstalled;
    private static volatile long sLastKeyAt;
    private static volatile SessionReport sLastSession;

    private record SessionReport(long at, int result, boolean locked) {}

    public static void apply(XC_LoadPackage.LoadPackageParam lpp) {
        ClassLoader cl = lpp.classLoader;

        Class<?> pwm;
        try {
            pwm = XposedHelpers.findClass(PWM, cl);
        } catch (Throwable t) {
            XposedBridge.log(Constants.TAG + " PhoneWindowManager not found: " + t);
            return;
        }

        hookSessionContext(cl);
        hookPowerKeyEvents(pwm);

        // 不再枚举固定名字。HyperOS 各版本把方法叫 launchSuperXiaoAi /
        // launchXiaoAiOnPowerLong / launchXiaoAiByLongPressPower / launchAiKey ...
        // 都不一样。统一用名字模式扫描：含 XiaoAi / Assist / Voice / AiKey 的全接管。
        int hooked = hookMiuiShortcutAction(cl);
        for (java.lang.reflect.Method m : pwm.getDeclaredMethods()) {
            if (!HookPolicy.shouldHookPowerMethod(m)) continue;
            hookOne(pwm, m);
            hooked++;
        }
        XposedBridge.log(Constants.TAG + " PowerKeyOverlay hooked " + hooked + " method(s)");
    }

    private static void hookSessionContext(ClassLoader loader) {
        try {
            Class<?> implementation = XposedHelpers.findClass(
                    "com.android.server.voiceinteraction.VoiceInteractionManagerServiceImpl", loader);
            for (java.lang.reflect.Method method : implementation.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!method.getName().equals("showSessionLocked") || types.length < 2
                        || types[0] != Bundle.class || types[1] != int.class) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        boolean[] session = PRIVATE_SESSION.get();
                        if (session == null) return;
                        param.args[1] = (Integer) param.args[1] & ~SCREEN_CONTEXT_FLAGS;
                        session[0] = true;
                        XposedBridge.log(Constants.TAG + " power session screen context suppressed");
                    }
                });
                sContextHookInstalled = true;
            }
        } catch (Throwable t) {
            XposedBridge.log(Constants.TAG + " screen context hook unavailable: " + t);
        }
    }

    private static int hookMiuiShortcutAction(ClassLoader cl) {
        try {
            Class<?> shortcutActions = XposedHelpers.findClass(MIUI_SHORTCUT_ACTIONS, cl);
            java.lang.reflect.Method method = shortcutActions.getDeclaredMethod(
                    "launchVoiceAssistant", String.class, android.os.Bundle.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 1
                                || !(param.args[0] instanceof String)
                                || !HookPolicy.isPowerLongPressShortcut(
                                (String) param.args[0])) {
                            return;
                        }
                        Context ctx = (Context) XposedHelpers.getObjectField(
                                param.thisObject, "mContext");
                        if (ctx != null && handleNativeAssistant(ctx)) {
                            param.setResult(true);
                            XposedBridge.log(Constants.TAG
                                    + " intercepted ShortCutActionsUtils#launchVoiceAssistant"
                                    + " for long_press_power_key");
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(Constants.TAG
                                + " shortcut callback failed; falling back to XiaoAi: " + t);
                    }
                }
            });
            XposedBridge.log(Constants.TAG + " hooked ShortCutActionsUtils#launchVoiceAssistant "
                    + method);
            return 1;
        } catch (Throwable t) {
            XposedBridge.log(Constants.TAG
                    + " ShortCutActionsUtils#launchVoiceAssistant hook failed: " + t);
            return 0;
        }
    }

    private static void hookOne(Class<?> clazz, java.lang.reflect.Method m) {
        XposedBridge.hookMethod(m, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.thisObject == null) return;
                    Context ctx = (Context) XposedHelpers
                            .getObjectField(param.thisObject, "mContext");
                    if (ctx != null && handleNativeAssistant(ctx)) {
                        param.setResult(interceptResult(m));
                    }
                } catch (Throwable t) {
                    XposedBridge.log(Constants.TAG + " " + m.getName()
                            + " callback failed; falling back to original method: " + t);
                }
            }
        });
        XposedBridge.log(Constants.TAG + " hooked " + clazz.getSimpleName()
                + "#" + m.getName() + " " + m);
    }

    static synchronized void observeTimingSettings(Context context) {
        if (context == null || sSettingsHandler != null) return;
        HandlerThread thread = new HandlerThread("PowerGeminiSettings");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        try {
            context.getContentResolver().registerContentObserver(TimingSettings.URI, false,
                    new ContentObserver(handler) {
                        @Override public void onChange(boolean selfChange) {
                            refreshTimingSettings(context);
                        }
                    });
            context.getContentResolver().registerContentObserver(Diagnostics.REQUEST, false,
                    new ContentObserver(handler) {
                        @Override public void onChange(boolean selfChange) {
                            reportDiagnostics(context);
                        }
                    });
            sSettingsHandler = handler;
            handler.post(() -> refreshTimingSettings(context));
        } catch (RuntimeException e) {
            thread.quitSafely();
            XposedBridge.log(Constants.TAG + " could not observe timing settings: " + e);
        }
    }

    static synchronized void reloadTimingSettings(Context context) {
        observeTimingSettings(context);
        if (context != null && sSettingsHandler != null) {
            sSettingsHandler.post(() -> refreshTimingSettings(context));
        }
    }

    private static void refreshTimingSettings(Context context) {
        try (Cursor cursor = context.getContentResolver().query(
                TimingSettings.URI, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                sConfiguredDelayMs = TimingSettings.sanitize(cursor.getInt(0));
                int screenContext = cursor.getColumnIndex(TimingSettings.SCREEN_CONTEXT);
                sScreenContextEnabled = screenContext < 0 || cursor.getInt(screenContext) != 0;
                XposedBridge.log(Constants.TAG + " configured hold: " + sConfiguredDelayMs + " ms");
            }
        } catch (RuntimeException e) {
            XposedBridge.log(Constants.TAG + " could not read timing settings: " + e);
        }
    }

    private static void reportDiagnostics(Context context) {
        Bundle report = new Bundle();
        report.putBoolean(Diagnostics.KEY_HOOK, sKeyHookInstalled);
        report.putInt(Diagnostics.LOCK_SCREEN_PERMISSION, lockScreenPermission(context));
        report.putLong(Diagnostics.LAST_KEY, sLastKeyAt);
        SessionReport last = sLastSession;
        if (last != null) {
            report.putLong(Diagnostics.LAST_SESSION, last.at());
            report.putInt(Diagnostics.SESSION_RESULT, last.result());
            report.putBoolean(Diagnostics.SESSION_LOCKED, last.locked());
        }
        try {
            Object manager = voiceInteractionManager();
            ComponentName service = manager == null ? null : (ComponentName)
                    XposedHelpers.callMethod(manager, "getActiveServiceComponentName");
            report.putString(Diagnostics.VOICE_SERVICE, service == null ? "" : service.getPackageName());
        } catch (Throwable t) {
            XposedBridge.log(Constants.TAG + " voice service diagnostic failed: " + t);
        }
        try {
            context.getContentResolver().call(Diagnostics.RESULT, Diagnostics.REPORT, null, report);
        } catch (RuntimeException e) {
            XposedBridge.log(Constants.TAG + " could not report diagnostics: " + e);
        }
    }

    private static int lockScreenPermission(Context context) {
        try {
            Class<?> activityManager = XposedHelpers.findClass("android.app.ActivityManager", null);
            int userId = (int) XposedHelpers.callStaticMethod(activityManager, "getCurrentUser");
            int uid = (int) XposedHelpers.callMethod(context.getPackageManager(),
                    "getPackageUidAsUser", Constants.GOOGLE_APP_PKG, userId);
            AppOpsManager appOps = context.getSystemService(AppOpsManager.class);
            int mode = (int) XposedHelpers.callMethod(appOps, "checkOpNoThrow",
                    MIUI_OP_SHOW_WHEN_LOCKED, uid, Constants.GOOGLE_APP_PKG);
            return switch (mode) {
                case AppOpsManager.MODE_ALLOWED -> Diagnostics.LOCK_SCREEN_ALLOWED;
                case AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED ->
                        Diagnostics.LOCK_SCREEN_DENIED;
                default -> Diagnostics.LOCK_SCREEN_UNKNOWN;
            };
        } catch (Throwable t) {
            XposedBridge.log(Constants.TAG + " could not check Google lock-screen permission: " + t);
            return Diagnostics.LOCK_SCREEN_UNKNOWN;
        }
    }

    private static void hookPowerKeyEvents(Class<?> pwm) {
        try {
            java.lang.reflect.Method method = pwm.getDeclaredMethod(
                    "interceptKeyBeforeQueueing", KeyEvent.class, int.class);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        KeyEvent event = (KeyEvent) param.args[0];
                        if (event.getKeyCode() != KeyEvent.KEYCODE_POWER) {
                            if (event.getAction() == KeyEvent.ACTION_DOWN
                                    && (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
                                    || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN)) {
                                cancelLongPressSequence();
                            }
                            return;
                        }
                        if (event.getAction() == KeyEvent.ACTION_UP || event.isCanceled()) {
                            cancelLongPressSequence();
                        } else if (event.getAction() == KeyEvent.ACTION_DOWN
                                && event.getRepeatCount() == 0) {
                            sLastKeyAt = System.currentTimeMillis();
                            Context context = (Context) XposedHelpers.getObjectField(
                                    param.thisObject, "mContext");
                            if (context != null) {
                                startLongPressSequence(param.thisObject, context, event.getDownTime());
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(Constants.TAG + " power key event hook failed: " + t);
                    }
                }
            });
            sKeyHookInstalled = true;
            XposedBridge.log(Constants.TAG + " power key timer installed");
        } catch (Throwable t) {
            XposedBridge.log(Constants.TAG + " power key event hook unavailable: " + t);
        }
    }

    private static void startLongPressSequence(Object pwm, Context context, long downAt) {
        synchronized (SEQUENCE_LOCK) {
            cancelLongPressSequence();
            sPhoneWindowManager = pwm;
            sPowerDownAt = downAt;
            final int delayMs = sPressDelayMs = sConfiguredDelayMs;
            sGeminiRunnable = () -> {
                if (requestAssistant(context, downAt)) {
                    XposedBridge.log(Constants.TAG + " " + delayMs + " ms power hold accepted by Google");
                }
            };
            sPowerMenuRunnable = () -> {
                synchronized (SEQUENCE_LOCK) {
                    if (sPowerDownAt != downAt || !sAssistantAccepted) return;
                }
                showPowerMenu();
            };
            SEQUENCE_HANDLER.postAtTime(sGeminiRunnable, downAt + delayMs);
            SEQUENCE_HANDLER.postAtTime(sPowerMenuRunnable, downAt + PowerKeyTimingPolicy.POWER_MENU_AT_MS);
        }
    }

    private static boolean handleNativeAssistant(Context context) {
        final long downAt;
        synchronized (SEQUENCE_LOCK) {
            downAt = sPowerDownAt;
            if (downAt < 0L) return false;
            if (sAssistantRequested && !sAssistantAccepted) return false;
            if (PowerKeyTimingPolicy.delayUntil(
                    downAt, SystemClock.uptimeMillis(), sPressDelayMs) > 0L) {
                // The ROM reached its assistant shortcut before our deadline.
                return true;
            }
        }
        return requestAssistant(context, downAt);
    }

    private static boolean requestAssistant(Context context, long downAt) {
        final Object pwm;
        synchronized (SEQUENCE_LOCK) {
            if (sPowerDownAt != downAt) return false;
            if (sAssistantRequested) return sAssistantAccepted;
            sAssistantRequested = true;
            pwm = sPhoneWindowManager;
        }
        int result = Diagnostics.FAILED;
        long requestedAt = System.currentTimeMillis();
        long identity = android.os.Binder.clearCallingIdentity();
        try {
            result = sendAssist(context);
        } catch (Throwable t) {
            XposedBridge.log(Constants.TAG + " assistant request failed: " + t);
        } finally {
            android.os.Binder.restoreCallingIdentity(identity);
        }
        boolean locked = false;
        try {
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            locked = keyguard != null && keyguard.isKeyguardLocked();
        } catch (RuntimeException e) {
            XposedBridge.log(Constants.TAG + " could not inspect lock screen state: " + e);
        }
        sLastSession = new SessionReport(requestedAt, result, locked);
        boolean accepted = result != Diagnostics.FAILED;
        synchronized (SEQUENCE_LOCK) {
            if (sPowerDownAt == downAt) {
                sAssistantAccepted = accepted;
                if (accepted && pwm != null) {
                    try {
                        XposedHelpers.setBooleanField(pwm, "mPowerKeyHandled", true);
                    } catch (Throwable t) {
                        XposedBridge.log(Constants.TAG + " unable to mark power key handled: " + t);
                    }
                }
            }
        }
        return accepted;
    }

    private static void cancelLongPressSequence() {
        synchronized (SEQUENCE_LOCK) {
            if (sGeminiRunnable != null) SEQUENCE_HANDLER.removeCallbacks(sGeminiRunnable);
            if (sPowerMenuRunnable != null) SEQUENCE_HANDLER.removeCallbacks(sPowerMenuRunnable);
            sGeminiRunnable = null;
            sPowerMenuRunnable = null;
            sPowerDownAt = -1L;
            sPhoneWindowManager = null;
            sAssistantRequested = false;
            sAssistantAccepted = false;
        }
    }

    private static void showPowerMenu() {
        Object pwm;
        synchronized (SEQUENCE_LOCK) {
            pwm = sPhoneWindowManager;
        }
        if (pwm == null) return;
        for (String method : new String[]{"showGlobalActions", "showGlobalActionsInternal",
                "showGlobalActionsDialog"}) {
            try {
                XposedHelpers.callMethod(pwm, method);
                XposedBridge.log(Constants.TAG + " power menu triggered at 5s power hold");
                return;
            } catch (Throwable ignored) {
                // HyperOS changes the private method name between releases.
            }
        }
        XposedBridge.log(Constants.TAG + " unable to find global actions method");
    }

    private static Object interceptResult(java.lang.reflect.Method m) {
        Class<?> rt = m.getReturnType();
        if (rt == Void.TYPE) return null;
        if (rt == Boolean.TYPE) return true;
        if (rt == Integer.TYPE) return 0;
        throw new IllegalArgumentException("Unsupported return type: " + rt);
    }

    private static int sendAssist(Context ctx) {
        // Bard 自身没声明 VoiceInteractionService，唯一弹 overlay 的路径是
        // Pixel 那条："framework → GSB VIS → GSB 把请求 forward 给 Bard overlay"。
        // 前提：voice_interaction_service 必须是 GSB 那个组件。MIUI 在开机后会
        // 把它回写成小爱 VIS，所以这里每次触发都现场改一次（system_server 自身
        // put 不受 WRITE_SECURE_SETTINGS 权限限制）。
        ensureGoogleVis(ctx);

        // 真·overlay：让当前 VoiceInteractionService 直接显示 session UI。
        boolean includeContext = sScreenContextEnabled;
        if (tryShowSession(includeContext)) return Diagnostics.VOICE_ACCEPTED;

        // SystemUI AssistManager (国行 HyperOS 大概率没接，留作兜底)。
        // SystemUI may add its own screen context, so use it only when sharing is enabled.
        if (includeContext && tryStatusBarStartAssist()) return Diagnostics.SYSTEM_UI_REQUESTED;

        XposedBridge.log(Constants.TAG
                + " overlay launch failed; skip SearchManager.launchAssist() to avoid Gemini app");
        return Diagnostics.FAILED;
    }

    private static void ensureGoogleVis(Context ctx) {
        try {
            android.content.ContentResolver cr = ctx.getContentResolver();
            String cur = android.provider.Settings.Secure.getString(cr,
                    Constants.SECURE_VOICE_INTERACT);
            if (Constants.GSB_ASSIST_SERVICE.equals(cur)) return;
            android.provider.Settings.Secure.putString(cr,
                    Constants.SECURE_VOICE_INTERACT, Constants.GSB_ASSIST_SERVICE);
            XposedBridge.log(Constants.TAG + " runtime forced voice_interaction_service "
                    + cur + " -> " + Constants.GSB_ASSIST_SERVICE);
        } catch (Throwable t) {
            XposedBridge.log(Constants.TAG + " ensureGoogleVis failed: " + t);
        }
    }

    private static Object voiceInteractionManager() {
        Class<?> sm = XposedHelpers.findClass("android.os.ServiceManager", null);
        IBinder binder = (IBinder) XposedHelpers.callStaticMethod(sm, "getService", "voiceinteraction");
        if (binder == null) return null;
        Class<?> stub = XposedHelpers.findClass(
                "com.android.internal.app.IVoiceInteractionManagerService$Stub", null);
        return XposedHelpers.callStaticMethod(stub, "asInterface", binder);
    }

    private static boolean tryShowSession(boolean includeContext) {
        if (!includeContext && !sContextHookInstalled) return false;
        boolean[] contextSuppressed = includeContext ? null : new boolean[1];
        if (contextSuppressed != null) PRIVATE_SESSION.set(contextSuppressed);
        try {
            Object svc = voiceInteractionManager();
            if (svc == null) return false;
            Object result = invokeKnown(svc, "showSessionForActiveService", includeContext);
            if (contextSuppressed != null && !contextSuppressed[0]) {
                XposedBridge.log(Constants.TAG + " screen context hook was not reached");
                return false;
            }
            if (result instanceof Boolean && !((Boolean) result)) return false;
            XposedBridge.log(Constants.TAG
                    + " launched via showSessionForActiveService() (overlay path)");
            return true;
        } catch (Throwable t) {
            XposedBridge.log(Constants.TAG + " showSessionForActiveService failed: " + t);
            return false;
        } finally {
            if (contextSuppressed != null) PRIVATE_SESSION.remove();
        }
    }

    private static android.os.Bundle assistArgs(boolean includeContext) {
        android.os.Bundle args = new android.os.Bundle();
        args.putInt("invocation_type", 7);              // power-long-press
        args.putString("invocation_source", "POWER_LONG_PRESS");
        args.putBoolean("request_assist_structure", includeContext);
        args.putBoolean("request_assist_screenshot", includeContext);
        return args;
    }

    private static boolean tryStatusBarStartAssist() {
        try {
            Class<?> serviceManager = XposedHelpers.findClass("android.os.ServiceManager", null);
            Object binder = XposedHelpers.callStaticMethod(serviceManager, "getService", "statusbar");
            if (binder == null) return false;
            Class<?> stub = XposedHelpers.findClass(
                    "com.android.internal.statusbar.IStatusBarService$Stub", null);
            Object statusBar = XposedHelpers.callStaticMethod(stub, "asInterface", binder);
            if (statusBar == null) return false;
            Object result = invokeKnown(statusBar, "startAssist", true);
            if (result instanceof Boolean && !((Boolean) result)) return false;
            XposedBridge.log(Constants.TAG
                    + " launched via IStatusBarService.startAssist() (overlay path)");
            return true;
        } catch (Throwable t) {
            XposedBridge.log(Constants.TAG + " statusbar startAssist failed: " + t);
            return false;
        }
    }

    private static Object invokeKnown(Object target, String name, boolean includeContext) throws Throwable {
        java.lang.reflect.Method best = null;
        for (java.lang.reflect.Method m : target.getClass().getMethods()) {
            if (!name.equals(m.getName())) continue;
            if (isKnownSignature(name, m.getParameterTypes())) {
                best = m;
                break;
            }
        }
        if (best == null) {
            for (java.lang.reflect.Method m : target.getClass().getDeclaredMethods()) {
                if (!name.equals(m.getName())) continue;
                if (isKnownSignature(name, m.getParameterTypes())) {
                    best = m;
                    break;
                }
            }
        }
        if (best == null) {
            logCandidateMethods(target, name);
            throw new NoSuchMethodError(target.getClass().getName() + "#" + name);
        }
        best.setAccessible(true);
        Object[] args = buildArgs(best.getParameterTypes(), includeContext);
        XposedBridge.log(Constants.TAG + " invoking " + methodSig(best));
        return best.invoke(target, args);
    }

    private static boolean isKnownSignature(String name, Class<?>[] pts) {
        if ("startAssist".equals(name)) {
            return pts.length == 1 && pts[0] == android.os.Bundle.class;
        }
        if (!"showSessionForActiveService".equals(name)) return false;

        boolean hasBundle = false;
        boolean hasFlags = false;
        for (Class<?> pt : pts) {
            if (pt == android.os.Bundle.class) {
                hasBundle = true;
                continue;
            }
            if (pt == int.class || pt == Integer.class) {
                hasFlags = true;
                continue;
            }
            if (pt == boolean.class || pt == Boolean.class) continue;
            if (pt == String.class) continue;
            if (pt == android.os.IBinder.class) continue;
            if (pt.isInterface() && pt.getSimpleName().endsWith("Callback")) continue;
            return false;
        }
        return hasBundle && hasFlags;
    }

    private static Object[] buildArgs(Class<?>[] pts, boolean includeContext) {
        Object[] args = new Object[pts.length];
        for (int i = 0; i < pts.length; i++) {
            Class<?> pt = pts[i];
            if (pt == android.os.Bundle.class) {
                args[i] = assistArgs(includeContext);
            } else if (pt == int.class || pt == Integer.class) {
                args[i] = SHOW_SOURCE_PUSH_TO_TALK | (includeContext ? SCREEN_CONTEXT_FLAGS : 0);
            } else if (pt == boolean.class || pt == Boolean.class) {
                args[i] = true;
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private static void logCandidateMethods(Object target, String name) {
        for (java.lang.reflect.Method m : target.getClass().getMethods()) {
            if (name.equals(m.getName())) {
                XposedBridge.log(Constants.TAG + " candidate " + methodSig(m));
            }
        }
        for (java.lang.reflect.Method m : target.getClass().getDeclaredMethods()) {
            if (name.equals(m.getName())) {
                XposedBridge.log(Constants.TAG + " candidate " + methodSig(m));
            }
        }
    }

    private static String methodSig(java.lang.reflect.Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getDeclaringClass().getName()).append("#").append(m.getName()).append("(");
        Class<?>[] pts = m.getParameterTypes();
        for (int i = 0; i < pts.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(pts[i].getName());
        }
        sb.append(")");
        return sb.toString();
    }
}
