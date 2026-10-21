package dev.opluskeycontrol.vector;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Finite, observation-only policy probe. Never enabled in the normal APK.
 * Records only the physically identified Plus scan code, and always proceeds.
 * No rule loading, binder/database access or application launch on the input path.
 */
final class PolicyInputProbe {
    static final String BUILD = "CPH2745_16.0.10.601(EX01)";
    private final XposedModule module;
    private final AtomicInteger remaining = new AtomicInteger(16);
    private volatile XposedInterface.HookHandle handle;
    private volatile long expiresAt;

    PolicyInputProbe(XposedModule module) { this.module = module; }

    boolean install(ClassLoader loader) {
        if (Build.VERSION.SDK_INT != 36 || !"CPH2745".equals(Build.MODEL) || !BUILD.equals(Build.DISPLAY)) {
            report("BLOCKED: firmware does not match observed profile");
            return false;
        }
        try {
            Class<?> ims = loader.loadClass("com.android.server.input.InputManagerService");
            Method method = ims.getDeclaredMethod("interceptKeyBeforeQueueing", KeyEvent.class, int.class);
            if (method.getReturnType() != int.class) {
                report("BLOCKED: method signature mismatch");
                return false;
            }
            expiresAt = SystemClock.uptimeMillis() + 600_000L;
            handle = module.hook(method).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                try {
                    if (SystemClock.uptimeMillis() >= expiresAt) detach();
                    else {
                        Object argument = chain.getArg(0);
                        if (argument instanceof KeyEvent) {
                            KeyEvent event = (KeyEvent) argument;
                            // Linux identity established by user physical DOWN/UP capture.
                            // Do NOT assume an Android keyCode from this scanCode.
                            if (event.getScanCode() == 735 && event.getKeyCode() != KeyEvent.KEYCODE_POWER) {
                                int budget = remaining.getAndDecrement();
                                if (budget > 0) report("PLUS_OBSERVATION keyCode=" + event.getKeyCode()
                                    + " scanCode=" + event.getScanCode() + " edge=" + event.getAction()
                                    + " repeat=" + event.getRepeatCount());
                                if (budget <= 1) detach();
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // An observation error cannot alter the original policy result.
                }
                // Exactly one proceed, outside error recovery: no accidental double dispatch.
                return chain.proceed();
            });
            report("PROBE_READY: read-only Plus, 16 events / 10 minutes, no suppression");
            return true;
        } catch (Throwable failure) {
            report("BLOCKED: probe attachment failed (" + failure.getClass().getSimpleName()
                + "); input untouched");
            return false;
        }
    }

    private void detach() {
        XposedInterface.HookHandle current = handle;
        handle = null;
        if (current != null) current.unhook();
    }
    private void report(String value) {
        try { module.log(Log.INFO, "OPlusKeyControl", value); } catch (Throwable ignored) { }
    }
}
