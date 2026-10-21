# Plus policy probe: concrete next experiment

This is **not a working remapper**. The normal APK does not register the input
probe. A separate opt-in build is needed. The first approved on-device attempt
did not load the policy probe; rollback is complete and the module is disabled.

## Device attempt and corrected scope (2026-10-22)

The user approved installation/activation and one reboot, then rebooted manually
and reconnected TCP. Boot completion was 1. Vector loaded VectorProbe in the
application process and reported runtime API 102 / version 3112. The boot's
system_server module log did not include OPlusKeyControl or PROBE_READY. No Plus
press was requested and no Android keycode was established by this attempt.

Root cause: this Vector build stores the system-server target under `system`,
not the Android package name `android`. The first APK's fixed scope and CLI row
both used `android`. At installed commit ddeed8ca, ModuleDatabase's
`systemServerModuleRows()` selects only app_pkg_name=system; ConfigCache handles
that special target explicitly. See the exact sources:

- https://github.com/JingMatrix/Vector/blob/ddeed8ca/daemon/src/main/kotlin/org/matrix/vector/daemon/data/ModuleDatabase.kt
- https://github.com/JingMatrix/Vector/blob/ddeed8ca/daemon/src/main/kotlin/org/matrix/vector/daemon/data/ConfigCache.kt

The corrected probe declares `system` and `dev.opluskeycontrol`. It builds and
passes APK metadata/API exclusion audit and apksigner verification. Generated
BuildConfig confirms POLICY_PROBE=true. No observer code changed. Packaging now
has an explicit --policy-probe option that rejects an unexpected scope and keeps
probe artifacts/evidence separate from the normal APK.

Rollback verified: only OPlusKeyControl disabled, scope empty, normal APK restored
with installed SHA-256 matching the normal artifact below. Every other Vector
module's enabled/disabled status matches the saved baseline. No settings, key
layouts, Power module or framework installation were changed. The corrected
probe was then installed/activated in the separately approved second attempt below.

## Second attempt and initialization diagnostics (2026-10-22)

After a separate approval and manual reboot, boot completion was 1 and Vector's
boot log confirmed VectorProbe loaded successfully in system_server (uid 1000).
The installed APK hash matched corrected probe 47d5d2df... . Scope was confirmed
system/self. There was still no PROBE_READY, BLOCKED, or callback exception log.
No physical Plus press was requested; Android keycode capture remains unverified.
This confirms module loading only, not invocation of onSystemServerStarting or
hook attachment. The exact reason that callback produced no diagnostic is not
established. Vector source dispatches it from StartBootstrapServicesHooker; normal
module creation occurs via ActivityThread.attach. Do not claim a proven callback
ordering defect without evidence.

The next local probe adds PROBE_STAGE diagnostics and attempts the same guarded
IMS hook during the observed system-server onModuleLoaded callback using the
thread context classloader. If the classloader/signature/firmware cannot be
resolved, attachment fails open with the exception class logged. The original
system-server-starting callback can retry. Synchronization and a success latch
prevent duplicate hook attachment; both attempts share one finite observer
budget. No extra hook target, action dispatch, wake lock or background thread was
added. This initialization change is built and audited locally, not runtime tested.

Second rollback is complete: module disabled, scope empty, latest normal APK
installed with matching hash below, all non-target Vector module statuses equal
to the baseline. Both builds and signature audits pass. JVM tests remain 76
passing; lint succeeds. Normal-build javap shows both onSystemServerStarting and
attachPolicy contain only return. The next probe requires a new activation/boot
approval; the second attempt's reboot authorization is already used.

## Third attempt: full boot and diagnostic mute boundary (2026-10-22)

After the user's manual resume, probe f84c173f... was installed and enabled in
system/self scope. An initial check showed an Android process restart only
(kernel uptime 8139.83 seconds, unchanged Vector daemon). The user then performed
a full reboot: uptime 120.44 seconds and boot completion 1. The new system_server
log again recorded module loading successfully, with no PROBE_STAGE/READY/BLOCKED.
No physical Plus press was requested, and no Android keycode was established.
Full reboot did not resolve the missing diagnostic; do not blame a stale daemon
or claim that the observer definitely failed to attach from absence of INFO logs.

Read-only analysis of the actual installed framework DEX found a logging gate:
VectorContext (obfuscated j2) log checks x1.b and skips priorities below ERROR
when it is true. Main.forkCommon initializes that process-local flag from the
framework service (or a muted fallback). This explains why module.log INFO is
not a reliable readiness channel during early boot; the actual flag value in
this system_server has not been measured. The matching GitHub source previously
consulted does not show the same logging gate, so binary behavior takes priority.

The next local probe uses Android Log.i directly for finite probe diagnostics,
independent of Vector's module.log mute flag, and records the module callback's
system-server boolean/process name before choosing a branch. Stage logging is
isolated so a logger exception cannot prevent attachment. Hook target, firmware
guard, 16-event budget, ten-minute uptime lease and pass-through remain unchanged.
Both builds, APK audits and signature verification pass. This change has not
been installed as a probe or runtime tested; no further reboot was initiated.

Third rollback verified: module disabled, scope empty, normal APK restored;
non-target module statuses match the baseline. A previously loaded observer may
remain resident until its existing bounded lease expires; disabling the module
and replacing its APK do not prove immediate unloading. It always calls proceed.

## Verified prerequisites

- Physical Plus: event0 / Linux 735 / BTN_TRIGGER_HAPPY32, user-confirmed capture.
- Firmware: CPH2745, CPH2745_16.0.10.601(EX01), API 36.
- Installed Vector CLI `status --json` now reports **runtime framework API 102**,
  version 2.2 / build 3112. This independently confirms the previous binary result.
- `services.jar` declares
  `InputManagerService.interceptKeyBeforeQueueing(KeyEvent,int):int`.
- The OEM AI Key method previously inspected is adjacent to keyboard shortcuts;
  it is not proven to own the physical Plus button. Do not suppress it blindly.

## Build separation

```powershell
# Normal build: self scope, BuildConfig.POLICY_PROBE=false
.\gradlew.bat :app:assembleDebug
# Experimental build: Vector system + self scope, observation only
.\gradlew.bat -PopcPolicyProbe=true :app:assembleDebug
python tools/package_debug.py --policy-probe
```

Build output paths overlap, so copy/audit each artifact before switching build
properties. The explicit property changes only module metadata and the compile
time probe flag. Package identity stays dev.opluskeycontrol; Android update
preserves rules/grants. Neither APK automatically enables Vector or remapping.

## What the probe does

At system-server module loading (and, if needed, its starting callback), it checks exact model/build/API and the exact
reflected signature before attaching one protective interceptor. Every invocation
calls `chain.proceed()` exactly once, returning the original result/exception.
Only scanCode 735 is logged; Power is explicitly excluded. It never changes
arguments/results, injects events, launches apps, reads rules/DB, performs IPC or
logs other keys. At most 16 matching events are logged. After ten minutes of
uptime, the next invocation unhooks it. No polling, permanent wakelock or daemon.

This reveals whether an Android event for Plus reaches policy and which Android
keycode it uses. If none arrives, investigation moves earlier in the OEM/native
input chain; the probe must not be represented as successful capture/remapping.

## Activation and rollback require separate approval

The supplied implementation brief requires consent before system_server hooks
and every reboot. User approval to test UI/manual actions did not explicitly
approve this system-server experiment. Do not enable it automatically.

Proposed exact scope: install the separately audited probe APK; enable only
dev.opluskeycontrol in Vector with system/self scope; **one reboot** to load it;
after normal boot the user presses/releases Plus once; inspect only the probe
tag; disable this module and restore the normal APK. No Power/combo/SOS test,
remapping, setting write, module-framework replacement or second reboot.

Recovery while ADB is available: use the installed Vector CLI's module-disable
operation for dev.opluskeycontrol only, then restore the normal APK. Current
normal APK/probe flag and module-disabled baseline must be retained for rollback.
Disabling scope may not unload code already resident, but this probe always
passes through and self-detaches; button behavior is unchanged while resident.
If ADB is unavailable during a boot failure, a separately verified existing
Vector/KSU recovery route is required; no guessed hardware recovery sequence is
claimed here. If that recovery route is unavailable, do not activate the probe.

Read-only recovery reconnaissance: su/ksud report KernelSU 3.4.2. The installed
abootloop script uses Power + VolumeDown, disables all KSU modules and reboots;
it is untested and must not be invoked as routine rollback. Official KernelSU
documents early-boot repeated VolumeDown Safe Mode (all modules disabled), but
availability/timing on this kernel has not been tested:
https://kernelsu.org/guide/rescue-from-bootloop.html . No flash/wipe is proposed.

The user explicitly chose TCP and manual reboot/reconnection after USB ADB did
not enumerate. Do not repeat USB troubleshooting or assume TCP survives reboot.
Before activation, report the exact scope; after installation/activation, prompt
the user to reboot and reconnect TCP. Never initiate an additional reboot.

Vector hot reload cannot be assumed to attach an initially disabled module to
an already running system_server. Source at installed commit ddeed8ca checks
for an existing module generation before hot reload. No reboot/restart has yet
been performed automatically; the user performed the first approved reboot.

## Replacement gate implemented and tested

The JVM PolicyGate requires a verified firmware/gesture proof, connected live
backend session, unexpired monotonic lease, explicit Root tier, master enabled
and a unique matching scheduled rule whose executor reports ready in that same
session. Otherwise it passes through. Emergency
disable/disconnect revoke replacement authority. The first adapter is Plus-only;
Power cannot be opted into it. This gate is **not yet connected to a runtime
action bridge**; tests establish decision logic, not on-device replacement.

## Current build evidence

- 76 JVM tests PASS, including 15 PolicyGate tests, no failures/errors.
- Normal/probe builds succeed; lint 0 errors / 15 warnings. The extra PrivateApi
  warning at the exact reflected method is intentional and not suppressed.
- Normal onSystemServerStarting disassembles to a single return in javap.
- Normal packaging audit confirms self scope and no bundled libxposed API.
- Both APKs pass apksigner. The original probe was installed then rolled back;
  the corrected probe is built locally only.

| Local artifact | SHA-256 |
|---|---|
| artifacts/OPlusKeyControl-0.1.0-dev-debug.apk (normal rollback) | d07dd79fc1047fde0771133a34e508f87d19f792d0586daa545279c9114cfa77 |
| artifacts/OPlusKeyControl-0.1.0-policy-probe-debug.apk (direct Android diagnostics, opt-in only) | 0d70caa9fef646019a39e2ec45ebe4d7a88ab9ceded39923f66dc1624e50a7df |

These are ignored local artifacts. Module status remains disabled; scope is
empty after rollback. The module baseline is saved privately for scoped rollback.
The failed first probe's SHA-256 was
fa30d472bf9d412cddb3321a2ef7146cabd29c4e4570bdac5387c96be7654aac.
