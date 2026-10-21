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
probe has not been installed/activated; another boot experiment requires consent.

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

At system-server initialization it checks exact model/build/API and the exact
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
| artifacts/OPlusKeyControl-0.1.0-dev-debug.apk (normal rollback) | 95980ccca6834bd8103215f0b75f6543c94f1837765f3c67962ded0ccbd0cd5c |
| artifacts/OPlusKeyControl-0.1.0-policy-probe-debug.apk (corrected, opt-in only) | 47d5d2df162f0d5a2fca4360a1051649aa28437513f6d1370ec7f6570da3a5de |

These are ignored local artifacts. Module status remains disabled; scope is
empty after rollback. The module baseline is saved privately for scoped rollback.
The failed first probe's SHA-256 was
fa30d472bf9d412cddb3321a2ef7146cabd29c4e4570bdac5387c96be7654aac.
