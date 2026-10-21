package dev.opluskeycontrol.vector;

import android.util.Log;
import io.github.libxposed.api.XposedModule;

/** API 101 compatibility probe. Deliberately registers ZERO hooks.
 * Scope is our own package only. No system_server entry point is implemented.
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
}
