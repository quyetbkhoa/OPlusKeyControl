# Setup, disable and recovery

## This build

No physical-device install or activation has been authorized yet. The debug APK
includes a self-scoped API 101 diagnostic entry with no hook registration. Do not
add system/framework packages to scope. Framework compatibility still needs an
approved self-scope probe test; `compileOnly` API is excluded from the APK.

The UI/action utility works without Vector. Root checking is an explicit `su -c id`
probe only; selecting Shizuku never invokes su. Accessibility is optional and
observation-only. No key events are consumed, no node content is read.

## Stop

1. Use **Tắt ngay / Disable** or the **Disable OPlusKeyControl** tile.
2. If recording, use the persistent **Stop and save** notification or UI button.
   Recording stop is available independently of master state and schedules.
3. Disable the app's Accessibility service in system settings if enabled.
4. Disable the optional self-scope Vector probe in the manager before removal.
5. Uninstall normally. No KSU helper or root-autostart file is installed.

These are design properties; actual physical-device uninstall/reboot testing
remains BLOCKED until approval. Public audio is intentionally retained after
uninstall, unlike private rules. Interrupted MediaRecorder output may be invalid;
pending-file recovery has not yet been verified.

## Future policy experiments (not shipped)

Before any system_server hook activation, require explicit approval, verify a
firmware-specific signature, prove narrow scope and balanced key handling, have
an independently accessible kill switch and package-removal liveness guard, and
document the installed Vector/KSU manager recovery method. Do not use a generic
reboot or guessed hardware recovery sequence as a proven rollback method.
Never replace an existing module or restart system_server during discovery.
Each reboot, emergency-button test, camera shutter and recording test needs its
own approval scope. Do not actually invoke SOS/hard-reset to test a guard.
