# Setup, disable and recovery

## This build

Normal APKs include a self-scoped API 101 diagnostic entry with no system hooks;
API 101 code and physical Plus observation were tested under runtime Vector API
102. The separately built runtime candidate is NOT device-accepted and must not
be enabled as part of ordinary UI installation. See RUNTIME_BACKEND.md for the
exact proposed first experiment and its additional root-child/keeper lifecycle.

The UI/action utility works without Vector. Root checking is an explicit `su -c id`
probe in normal mode; Root action tests invoke su only after explicit selection
and confirmation. The runtime build may run a transient scope-watcher child when
Root is selected and enabled. Selecting Shizuku never invokes su. Accessibility is optional and
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

## Experimental runtime rollback

Use only this module's CLI operations, never a framework-wide disable/reset:

```text
/data/adb/lspd/cli modules disable dev.opluskeycontrol --json
/data/adb/lspd/cli scope rm dev.opluskeycontrol system/0 dev.opluskeycontrol/0 --json
```

Then restore the audited normal APK with Android's update install (`install -r`),
preserving rules. The runtime listens for its package update/removal and attempts
to revoke authority, unhook and release its worker/binding. This teardown is an
implemented design, not a tested uninstall guarantee. The stdin lifetime pipe is
intended to exit the transient root watcher when its app dies. Check the actual
process list/helper lifetime during the approved device experiment. Do not declare
clean uninstall from a successful build. Existing audio/photos are retained.
No additional reboot is included in this rollback. If teardown fails, resident
input code must still pass new presses through when its Binder peer is gone.

## Policy experiment approval

Before any system_server hook activation, require explicit approval, verify a
firmware-specific signature, prove narrow scope and balanced key handling, have
an independently accessible kill switch and package-removal liveness guard, and
document the installed Vector/KSU manager recovery method. Do not use a generic
reboot or guessed hardware recovery sequence as a proven rollback method.
Never replace an existing module or restart system_server during discovery.
Each reboot, emergency-button test, camera shutter and recording test needs its
own approval scope. Do not actually invoke SOS/hard-reset to test a guard.
