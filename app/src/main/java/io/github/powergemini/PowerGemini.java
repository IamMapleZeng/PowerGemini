package io.github.powergemini;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.error.HookFailedError;

public final class PowerGemini extends XposedModule {
    private static final String TAG = "PowerGemini";
    private static final String POWER_SHORTCUT = "long_press_power_key";
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";
    private static final int SHOW_WITH_ASSIST = 1;
    private static final int SHOW_WITH_SCREENSHOT = 1 << 1;
    private static final int SHOW_SOURCE_PUSH_TO_TALK = 1 << 4;

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        ClassLoader loader = param.getClassLoader();
        hookShortcut(loader);
        hookPowerRule(loader);
    }

    private void hookShortcut(ClassLoader loader) {
        try {
            Class<?> shortcuts = Class.forName(
                    "com.miui.server.input.util.ShortCutActionsUtils", false, loader);
            Method launch = shortcuts.getDeclaredMethod(
                    "launchVoiceAssistant", String.class, Bundle.class);
            if (launch.getReturnType() != boolean.class) {
                log(Log.WARN, TAG, "Unsupported launchVoiceAssistant return type");
                return;
            }
            Field context = shortcuts.getDeclaredField("mContext");
            context.setAccessible(true);
            hook(launch).intercept(chain -> {
                if (POWER_SHORTCUT.equals(chain.getArg(0))
                        && tryLaunchGoogle(context, chain.getThisObject(), loader)) {
                    return true;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "Power shortcut hook installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError | HookFailedError e) {
            log(Log.WARN, TAG, "Power shortcut unavailable on this ROM", e);
        }
    }

    private void hookPowerRule(ClassLoader loader) {
        try {
            Class<?> rule = Class.forName(
                    "com.android.server.input.shortcut.singlekeyrule.PowerKeyRule", false, loader);
            Class<?> base = Class.forName(
                    "com.android.server.policy.MiuiSingleKeyRule", false, loader);
            Method getFunction = base.getDeclaredMethod("getFunction", String.class);
            getFunction.setAccessible(true);
            Field context = findContextField(rule);
            int installed = 0;
            for (Method method : rule.getDeclaredMethods()) {
                if (!method.getName().equals("onMiuiLongPress")
                        || method.getReturnType() != void.class) continue;
                hook(method).intercept(chain -> {
                    Object receiver = chain.getThisObject();
                    if (usesXiaoAi(getFunction, receiver)
                            && tryLaunchGoogle(context, receiver, loader)) {
                        return null;
                    }
                    return chain.proceed();
                });
                installed++;
            }
            log(Log.INFO, TAG, "Power rule hooks installed: " + installed);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError | HookFailedError e) {
            log(Log.WARN, TAG, "Power rule unavailable on this ROM", e);
        }
    }

    private static Field findContextField(Class<?> type) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("mContext");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Some ROMs keep mContext in the parent class.
            }
        }
        throw new NoSuchFieldException("mContext");
    }

    private boolean usesXiaoAi(Method getFunction, Object receiver) {
        try {
            return "launch_voice_assistant".equals(getFunction.invoke(receiver, POWER_SHORTCUT));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            log(Log.WARN, TAG, "Could not read the power key shortcut", e);
            return false;
        }
    }

    private boolean tryLaunchGoogle(Field contextField, Object receiver, ClassLoader loader) {
        long identity = Binder.clearCallingIdentity();
        try {
            Context context = (Context) contextField.get(receiver);
            Class<?> am = Class.forName("android.app.ActivityManager", false, loader);
            int userId = (Integer) am.getMethod("getCurrentUser").invoke(null);
            Method getSetting = Settings.Secure.class.getMethod(
                    "getStringForUser", ContentResolver.class, String.class, int.class);
            String service = (String) getSetting.invoke(null, context.getContentResolver(),
                    "voice_interaction_service", userId);
            ComponentName configured = ComponentName.unflattenFromString(
                    service == null ? "" : service);
            if (configured == null || !GOOGLE.equals(configured.getPackageName())) {
                log(Log.INFO, TAG, "Set Google as the default digital assistant");
                return false;
            }

            Class<?> serviceManager = Class.forName("android.os.ServiceManager", false, loader);
            IBinder binder = (IBinder) serviceManager.getMethod("getService", String.class)
                    .invoke(null, "voiceinteraction");
            if (binder == null) return false;
            String interfaceName = "com.android.internal.app.IVoiceInteractionManagerService";
            Class<?> stub = Class.forName(interfaceName + "$Stub", false, loader);
            Object manager = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            Class<?> managerType = Class.forName(interfaceName, false, loader);
            ComponentName active = (ComponentName) managerType
                    .getMethod("getActiveServiceComponentName").invoke(manager);
            if (!configured.equals(active)) {
                log(Log.INFO, TAG, "Google voice interaction service is not active");
                return false;
            }

            Bundle args = new Bundle();
            args.putInt("invocation_type", 7);
            args.putString("invocation_source", "POWER_LONG_PRESS");
            int flags = SHOW_WITH_ASSIST | SHOW_WITH_SCREENSHOT | SHOW_SOURCE_PUSH_TO_TALK;
            boolean shown = showSession(managerType, manager, args, flags);
            log(Log.INFO, TAG, shown ? "Google assistant session requested"
                    : "Google assistant rejected the session");
            return shown;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            log(Log.WARN, TAG, "Google session unavailable; using original shortcut", e);
            return false;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private static boolean showSession(Class<?> type, Object manager, Bundle args, int flags)
            throws ReflectiveOperationException {
        ClassLoader loader = type.getClassLoader();
        Class<?> callback = Class.forName(
                "com.android.internal.app.IVoiceInteractionSessionShowCallback", false, loader);
        Method show;
        try {
            show = type.getMethod("showSessionForActiveService", Bundle.class, int.class,
                    String.class, callback, IBinder.class);
        } catch (NoSuchMethodException olderSignature) {
            show = type.getMethod("showSessionForActiveService", Bundle.class, int.class,
                    callback, IBinder.class);
            return Boolean.TRUE.equals(show.invoke(manager, args, flags, null, null));
        }
        return Boolean.TRUE.equals(show.invoke(manager, args, flags, null, null, null));
    }
}
