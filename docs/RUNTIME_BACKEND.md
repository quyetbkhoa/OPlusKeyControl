# Experimental runtime backend — not device-accepted

## Latest fix: SELinux-denied socket replaced with Binder

On 2026-10-22 live inspection confirmed the policy worker and BACKEND_READY receiver
in system_server. An explicit ready broadcast produced `avc: denied { connectto }`
from `u:r:system_server:s0` to the app's `u:r:untrusted_app:s0` Unix socket.
Thus the loaded hook could not connect to the previous app transport.

The replacement uses the non-exported BackendKeeperService Binder. App transactions
require UID 1000; the system-side callback checks the actual installed app UID and
current callback identity. Configuration is capped at 256 KiB, uses versioned
snapshots, and does not authorize actions outside current rules. Binder death and
explicit revocation invalidate ownership; no SELinux or framework setting changes.
Pending app authorization can request a bounded retry; explicit revocation cannot.
Stopped-app flags prevent a physical key from auto-starting a force-stopped app.
The scope watcher remains transient and unchanged.

Installed APK SHA-256:
`4e65ee5fac465278ea62a098a552459b68b3459645ac5c7524697d3084c35d91`.
Device file hash matched; private stored configuration was byte-for-byte identical
before and after the update. UI opened. 105 JVM tests passed; lint 0 errors and
22 warnings. A manual reboot is pending to activate and validate the Binder code.
No physical action, rule modification or reboot was performed by the agent.
The sections below retain earlier evidence and historical artifact hashes.

The physical observation test established Plus keyCode 780 / scanCode 735, not
permission to replace OEM behavior. This candidate is a separate `-PopcRuntime=true`
APK. On 2026-10-22 the runtime APK was installed successfully with user approval
and its launch intent was accepted. The subsequent UI dump failed because TCP
went offline; reconnect was refused. UI rendering and installed-file hash were
therefore not independently checked after installation. No hook activation or
reboot was performed. Scope was empty after installation; module disabled was
verified immediately before installation. See MANUAL_TEST.vi.md for handoff.

## What is implemented

2026-10-22 follow-up: rule preparation now permits offline Plus single/double/long
in the opt-in Root build, while runtime consumption still requires a live peer,
valid authority and full coverage. UI shows disconnected/action prerequisites.
Early context initialization registers readiness receivers before querying
ActivityManager. A bounded app-ready retry window avoids dropping readiness
while another connection is unwinding; finite boot initialization lasts at most
90 seconds. Warning-level bounded diagnostics identify entry/profile/context/
connection failure. The updated APK is installed and its device hash matched
`2645a14e7c913d438f8b2fdc9213c892a08e5149438bc0104ef4b9aa9625d1b5`.
Rule switches were enabled in the real UI; no rule/action was activated by the
agent. New system-server code awaits a manual reboot and remains unverified.

- Exact API 36 / CPH2745 / CPH2745_16.0.10.601(EX01) guard and exact IMS signature.
  Only keyCode 780, scanCode 735, input device gpio-keys may be owned. Foreground
  user must be user 0. User switches revoke the connection. Other keys always
  proceed; Power/Volume cancel pending Plus gestures without being consumed.
- Single/double/long domain recognition, monotonic deadlines, bounded duplicate
  cache, one-shot timers, active schedule resolution and explicit Root ownership.
- A **complete-coverage** raw adapter: SINGLE, DOUBLE and LONG must each resolve
  uniquely to an enabled, ready action at the first DOWN. Missing coverage,
  conflicts, active Plus chords, disabled master, absent configuration or session
  loss pass new presses through to OEM. Partial mappings need a future verified
  OEM semantic adapter; they are not silently treated as working remaps.
- An already owned DOWN has its paired UP swallowed even if authority disappears
  mid-press; pending actions are cancelled. This prevents an orphan UP entering
  OEM. It does not replay an already owned press when an executor crashes. New
  presses pass through. Schedule/config changes during a held gesture may cancel
  that gesture rather than execute an action from an obsolete configuration.
- Private Binder service transport: both endpoints check Binder caller UID against
  system UID / PackageManager's actual app UID. Bounded payloads, versioned snapshots,
  fixed enums/rule IDs, no arbitrary commands or intents on the wire. The app
  re-resolves rule/time/master before running an app-side action. The input path
  does not call Binder, database, package manager or su. A bounded handoff queue
  runs on worker threads. Transactions are one-way, configuration capped at 256 KiB;
  binder death, explicit disconnect and package/scope changes revoke snapshots.
  A force-stopped app is never auto-bound from a physical key. No permanent wake lock.
  At boot, system-context initialization has at most 60 attempts separated by
  1.5 seconds because ActivityManager may not yet exist at module loading. This
  bounded bootstrap retry stops; it is not standby polling. Later reconnects are
  driven by an app-ready broadcast or physical Plus event.
- Scope authorization uses a transient root app_process child containing the
  app's own ScopeWatchMain. A FileObserver watches only database writes/moves;
  SHM/read events are ignored. An event revokes authorization before a bounded
  debounce and fresh CLI check of this module's enabled flag and system/0 scope.
  No idle polling. The child blocks on stdin; app death closes the pipe and exits
  the process. This cleanup design still needs force-stop/uninstall device tests.
  No module, script, system setting or auto-start root daemon is installed.
- system_server binds an empty private keeper service while an authenticated
  connection exists. No idle foreground service. Binding is released on loss of
  authority. A runtime-only boot receiver reloads the user's saved tier after
  normal boot/user unlock, never enables master or rules. Before first unlock,
  direct-boot operation is not claimed.
- Package replacement/removal attempts to unhook the input interceptor, release
  receivers/bindings and stop its worker threads. A runtime APK update therefore
  needs a freshly approved full boot to activate the new generation. This
  teardown still needs device testing; no clean-uninstall claim is made yet.

## Actions and boundaries

- Torch uses CameraManager callbacks and CAMERA permission; manual behavior was
  tested previously, but Binder-triggered action delivery is NOT_TESTED.
- App/Activity root launching uses validated ComponentName tokens, quoted shell
  arguments, the app's own user ID, enabled-target checks and Keyguard protection.
  Permission-protected activities remain blocked. Internal activities may be
  selected explicitly in Root mode, but require a successful manual target test
  before mapping readiness. Root is the caller, never the target UID/signature.
- Recorder retains the real microphone FGS, notification, Stop action, privacy
  indicators and auto-stop. Root handoff originates its service request from
  system_server, relying on Android's system-component exception. This is an
  implementation, not evidence that ColorOS accepts the background start. The
  originating trigger overrides later schedule actions while recording, as long
  as full raw-gesture coverage is still available. If coverage disappears, Stop
  in the notification/UI remains available; originating-gesture stop across that
  case is not guaranteed and needs a semantic adapter.
- Stock camera has an explicit root test adapter: resolve the system stock
  component, require resumed camera + active CameraService client, emit one
  KEYCODE_CAMERA pair targeted to the camera UID, then require a new non-pending MediaStore row owned by
  com.oplus.camera. It never retries a shutter or closes camera. No image bytes
  or names are read. Root photo-ID queries happen only during a requested action.
  No mapping readiness until a manual test succeeds for this firmware/package.
  The shutter key, secure mode, format parsing and acknowledgement remain
  NOT_TESTED on-device. This is not a camera-complete acceptance result.
  The transient CameraShutterMain root child uses the exact locally inventoried
  InputManagerGlobal.injectInputEvent(InputEvent,int,int):boolean signature, not
  an untargeted shell input fallback. The last parameter pins the destination UID;
  no target-UID/signature impersonation occurs. Failure to resolve/invoke it aborts.

Basic still observes Volume and always returns false. Shizuku remains permission
detection, not an input source. Volume replacement, partial Plus ownership,
Power double, chords, multi-profile and idle/deep-sleep delivery remain unsupported
or NOT_TESTED. They cannot be filled in by enabling an unverified raw grab.

Primary implementation references:

- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/services/camera/libcameraservice/CameraService.cpp
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/hardware/input/InputManagerGlobal.java

The OEM binary (private, not distributed) contains
StrategyActionButtonKeyLaunchApp.actionInterceptKeyBeforeQueueing(KeyEvent,int,int,boolean,boolean).
It handles raw 780 and injects 781 short / 782 long events. This static evidence
is a lead for partial mapping; it has not been observed as the active dispatch
path or suppressed at runtime. Do not hook it based on its name alone.

## Build isolation and verification

```powershell
.\gradlew.bat :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python tools/package_debug.py
.\gradlew.bat -PopcRuntime=true :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python tools/package_debug.py --runtime
```

Probe and runtime flags cannot both be true. Output paths overlap; packaging
copies artifacts separately. The dependency-free DEX audit checks scope, excluded
libxposed classes and that disabled entry callbacks/helpers have no executable
hook logic. Normal APK self scope and disabled boot/keeper components remain the
rollback baseline. Build/test/signature success is not a device remapping test.

## Concrete first device experiment — approval required

1. Preserve existing rules. Install audited runtime APK, enable only this Vector
   module with system/0 and self/0 scope, then one manual full reboot/TCP reconnect.
2. Open UI, choose Root, approve its su request, explicitly enable master. Verify
   helper/peer/keeper lifecycle before creating active mappings. Test no-rule
   pass-through first. No Accessibility, camera, microphone or Power tests.
3. With approval, use three temporary all-day Plus SINGLE/DOUBLE/LONG torch rules
   so raw ownership is complete. Physically test single, double, long and no
   duplicate action. Never enable one temporary rule and claim partial support.
4. Master disable, module/scope disable and app force-stop must immediately revoke
   authority for new presses. Verify no stuck press, no helper left after app death
   and no non-target module change. Uninstall needs its own concrete approval.
5. Restore the original rules, disable only this module, clear its scope, restore
   normal APK. No extra reboot, recording, shutter, OEM setting write or framework
   replacement is included in this first experiment.

Every row in that experiment is currently NOT_TESTED. Lifecycle failure blocks
broader tests. Camera/photo and microphone/background tests require a separate
explicit scope and do not follow merely from installing the runtime APK.

## Broader action test option (only with explicit additional scope)

After the lifecycle/torch checks pass, a single expanded consent may include:
launch this app's own Activity, launch one user-selected allowed target, request
exactly one stock photo without viewing/uploading it, and one microphone recording
of at most ten seconds with notification/indicator retained. Preserve the resulting
photo/audio locally. Do not uninstall/clear app data or test Power/SOS/chords under
this scope. Restore the rule snapshot and normal APK at the end. No additional
boot is authorized by this expanded action scope. Internal activities and secure
camera remain unverified unless those specific cases are actually exercised.

## Local build evidence

- 101 JVM tests, zero failures/errors/skips (76 previous + 20 ownership tests +
  5 bounded wire tests). These do not simulate Android lifecycle/SELinux/FGS.
- Normal and runtime assemble + lint pass: 0 errors / 22 warnings. Private API and
  guarded runtime warnings are retained, not hidden by a lint baseline.
- Probe assemble and all three packaging/signature audits pass. DEX audit checks
  compiled entry flags and exclusion of libxposed implementation classes. A
  deliberate wrong-mode packaging attempt was rejected without overwriting the
  runtime APK. Vietnamese/English resource keys match (150).
- CI now defines separate normal/runtime matrix jobs; it has not run on GitHub.
- Runtime installation succeeded; launch intent accepted, but subsequent TCP
  loss prevented visual UI acceptance and installed-file hash verification.
  No activation, remapping test, permission change or reboot occurred here.
  Latest runtime build/tests/signature audit passed after the held-key draining
  regression fix. Normal/probe rollback artifacts below predate that domain fix;
  their system hook runtime remains disabled. Lint preceded the domain-only fix.

| Artifact | SHA-256 |
|---|---|
| artifacts/OPlusKeyControl-0.1.0-runtime-debug.apk | 73969e71f4e9931e609255bca64d9a0d30e355c0ce5f007b93684ee70d95c512 |
| artifacts/OPlusKeyControl-0.1.0-dev-debug.apk | c207bb4ba9df9e5d54b89cd6715de7aa600504d80e086a98154c861bec1f8123 |
| artifacts/OPlusKeyControl-0.1.0-policy-probe-debug.apk | 966160cbd5a7925358a3e0e215b7657b3e4f0348b6c70e4de34e366f00366e14 |
