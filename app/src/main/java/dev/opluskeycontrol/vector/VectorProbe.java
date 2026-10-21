package dev.opluskeycontrol.vector;

import android.util.Log;
import dev.opluskeycontrol.BuildConfig;
import io.github.libxposed.api.XposedModule;

/** API 101 entry. The normal build registers zero hooks and has self scope.
 * The explicitly built policy probe adds a finite, pass-through observation hook.
 * Reading LIB_API would merely report a compile-time constant; use getApiVersion.
 */
public final class VectorProbe extends XposedModule {
    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        if (param.isSystemServer() || !"dev.opluskeycontrol".equals(param.getProcessName())) return;
        try {
            log(Log.INFO, "OPlusKeyControl", "Probe only; no hooks. Runtime API=" + getApiVersion()
                    + ", framework=" + getFrameworkName() + ", version=" + getFrameworkVersionCode());
        } catch (Throwable ignored) {
            // A diagnostic failure must not affect the host or input policy.
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        if (!BuildConfig.POLICY_PROBE) return;
        // Probe implementation does not consume, rewrite or inject any event.
        new PolicyInputProbe(this).install(param.getClassLoader());
    }
}
