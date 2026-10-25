package dev.opluskeycontrol.vector;

import android.util.Log;
import dev.opluskeycontrol.BuildConfig;
import io.github.libxposed.api.XposedModule;

/** API 101 entry. The normal build registers zero hooks and has self scope.
 * The explicitly built policy probe adds a finite, pass-through observation hook.
 * Reading LIB_API would merely report a compile-time constant; use getApiVersion.
 */
public final class VectorProbe extends XposedModule {
    private PolicyInputProbe policyProbe;
    private boolean policyAttached;
    private RootPolicyBackend runtime;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        if (BuildConfig.POLICY_PROBE) {
            reportStage("module_loaded systemServer=" + param.isSystemServer()
                    + " process=" + param.getProcessName());
        }
        if (param.isSystemServer()) {
            if (BuildConfig.POLICY_RUNTIME) attachRuntime(Thread.currentThread().getContextClassLoader());
            if (BuildConfig.POLICY_PROBE) {
                attachPolicy(Thread.currentThread().getContextClassLoader(), "module_loaded");
            }
            return;
        }
        if (!"dev.opluskeycontrol".equals(param.getProcessName())) return;
        try {
            log(Log.INFO, "OPlusKeyControl", "Probe only; no hooks. Runtime API=" + getApiVersion()
                    + ", framework=" + getFrameworkName() + ", version=" + getFrameworkVersionCode());
        } catch (Throwable ignored) {
            // A diagnostic failure must not affect the host or input policy.
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        if (BuildConfig.POLICY_RUNTIME) attachRuntime(param.getClassLoader());
        if (!BuildConfig.POLICY_PROBE) return;
        attachPolicy(param.getClassLoader(), "system_server_starting");
    }

    private synchronized void attachRuntime(ClassLoader loader) {
        if (!BuildConfig.POLICY_RUNTIME || android.os.Build.VERSION.SDK_INT < 36) return;
        try {
            Log.w("OPlusKeyControl", "RUNTIME_ENTRY loader=" + loader.getClass().getName());
            if (runtime == null) runtime = new RootPolicyBackend(this);
            runtime.install(loader);
        } catch (Throwable failure) {
            Log.e("OPlusKeyControl", "RUNTIME_ENTRY_FAILED", failure);
        }
    }

    private synchronized void attachPolicy(ClassLoader loader, String stage) {
        if (!BuildConfig.POLICY_PROBE || policyAttached) return;
        reportStage(stage);
        try {
            // The observed module-loaded callback is also a valid attachment opportunity
            // if its context loader resolves the exact guarded IMS method. No new hook target.
            if (policyProbe == null) policyProbe = new PolicyInputProbe(this);
            policyAttached = policyProbe.install(loader);
        } catch (Throwable ignored) {
            // Diagnostics and attachment must never escape into framework initialization.
        }
    }

    private void reportStage(String stage) {
        try { Log.i("OPlusKeyControl", "PROBE_STAGE " + stage); } catch (Throwable ignored) { }
    }
}
