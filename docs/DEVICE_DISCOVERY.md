# Device discovery — 2026-10-22

Initial read-only ADB inspection. The original reconnaissance below predates the
separately approved action and probe tests; see the current follow-up first.
During the initial inspection, no APK installation, settings write, module change,
input injection, process restart, EVIOCGRAB or reboot was performed.
Device serial, installed personal app lists and private logs are intentionally omitted.

## Current probe follow-up

User-confirmed physical Plus input was captured by the approved read-only IMS
probe: **Android keyCode 780 / scanCode 735**, DOWN and UP, repeat=0. Four pairs
were captured; one-to-one physical press accounting has not been established.
This validates input observation in system_server on the exact firmware below,
not replacement of OEM actions or capture while locked/asleep. The probe was
then disabled, its scope cleared and the normal APK restored; all other Vector
module statuses match the saved baseline. API 101.0.1 code executed under runtime
Vector API 102. Full experiment and limitations: [POLICY_PROBE.md](POLICY_PROBE.md).

## Original reconnaissance

| Classification | Finding | Evidence |
|---|---|---|
| FACT | CPH2745 / CPH2745IN, Android 16 API 36 | getprop ro.product.model; ro.build.display.id; ro.build.version.sdk |
| FACT | Build CPH2745_16.0.10.601(EX01) | getprop ro.build.display.id |
| FACT | Current adb shell UID 0, SELinux ksu domain; su separately UID 0 | shell id; shell su -c id |
| FACT | adbd currently running | getprop init.svc.adbd |
| FACT | Vector v2.2 build 3112-ddeed8ca | /data/adb/modules/zygisk_vector/module.prop |
| FACT | Existing Power Key Torch Controller v1.1.0 | module.prop: Power double toggle; single screen-off turns torch off |
| FACT | event0 gpio-keys advertises BTN_TRIGGER_HAPPY32 (Linux 735) | getevent -pl; /proc/bus/input/devices |
| FACT | event1 pmic_pwrkey: Power; event2 gpio-keys: VolDown; event3 pmic_resin: VolUp | getevent -pl |
| FACT | gpio-keys.kl maps 766 to CAMERA, 115/114 to volume | cat /system/usr/keylayout/gpio-keys.kl |
| INFERENCE | event0 may be Plus Key; Linux 735 vs keylayout 766 mismatch needs physical capture | No physical-button experiment yet; no Plus keycode is enabled in app |
| FACT (follow-up) | event0 is physical Plus: BTN_TRIGGER_HAPPY32 DOWN/UP | User pressed once during finite getevent capture; see DEVICE_ACTION_TESTS.md; Android translation still unknown |
| FACT | event0–3 device/power/wakeup reports enabled | sysfs read; does NOT prove delivery in deep sleep |
| FACT | Camera com.oplus.camera v6.070.230 | dumpsys package scoped to camera |
| FACT | Stock normal intent -> .component.CameraImageActivity; secure -> .Camera | cmd package resolve-activity with -p com.oplus.camera |
| FACT | Unscoped still-camera intent resolves to ResolverActivity | Other handlers exist; do not trust default resolution |
| FACT | Device Dozing, idle mode false at observation | selected dumpsys power fields |
| FACT (static binary) | Installed vector.dex XposedInterface.getApiVersion returns 102 | Pulled read-only; disassembled method is const/16 102; runtime probe not activated |
| UNKNOWN | API 101 module compatibility at runtime | Probe compiles against 101.0.1; installed binary API is 102; requires approved self-scope test |
| UNKNOWN | Reliable stock readiness, shutter command, photo acknowledgement | An intent filter alone is not evidence of capture |
| UNKNOWN | OEM Power-double hook point and pass-through guarantees | Firmware policy source/signatures not yet verified |
| BLOCKED | Install, accessibility activation, grant, root hook, camera capture, recording, reboot/uninstall tests | Require separate device approval per supplied brief |

`adb root` was deliberately not invoked: it can restart adbd. Existing root-shell
identity was observed without changing daemon state. KSU is evidenced by the su
identity and /data/adb/ksu; exact manager/kernel version remains unverified.

## Architecture decision

Pure JVM domain -> event ownership -> gesture candidates -> live zoned-time rule
resolver -> serialized Android actions. DataStore stores a versioned, validated
configuration. Basic accessibility accepts only the verified public Volume keys.
Power events never enter this filter. Plus and Power rules may be prepared disabled;
no runtime claim until a firmware-specific backend is validated.

Basic currently observes only and never consumes events: public single capture
would swallow an unassigned long gesture without a verified replay/policy path.
Shizuku binder/permission detection is real; Shizuku is not an input-capture API.
Root is opt-in, no automatic su fallback from Basic/Shizuku. No system_server hook
is registered. The APK contains an API 101.0.1 compatibility probe scoped only to
its own package; this is not a remapping module.
User subsequently confirmed the Power Key Torch Controller was manually disabled
and instructed us to ignore it. No module state was changed by this project.
Follow-up read-only `ls` confirmed its `disable` file exists.
Experimental adapters must fail open; no input grabbing/reinjection is shipped.

## Primary sources consulted

- https://github.com/JingMatrix/Vector/blob/master/xposed/README.md
- https://github.com/JingMatrix/libxposed-api (master currently API 102)
- https://github.com/libxposed/example/blob/master/app/src/main/resources/META-INF/xposed/module.prop
- https://github.com/RikkaApps/Shizuku-API
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/service-types

## Approved-test boundary and rollback

User approved installing the debug APK and opening/testing UI only. No runtime
permission grant, Accessibility/Vector activation, remapping, recording, photo,
reboot or uninstall was authorized in this approval. ADB subsequently reported no
connected devices. Update: user reconnected over TCP, installation and initial
Overview rendering passed; see DEVICE_UI_CHECK.md. No expanded permission scope.
Future action tests require their own scope. Disable from UI/tile before an
approved uninstall; verify volume pass-through and removal. Camera capture,
recording, internal-activity launch and every reboot need their own concrete scope.
For future Vector experiments: validate API 101-on-102 runtime and firmware method,
document manager disable/recovery before activation. A bootloop-risk experiment is
not authorized by permission to build/install the UI.

## Additional offline binary evidence

Private copies are Git-ignored and never packaged or redistributed:

| File | SHA-256 |
|---|---|
| vector.dex | fc5ae6b445a9b35216474176ced3e2e874067e1ccb4b8a146d88747b02cfaea1 |
| oplus-services.jar | 1db4bc70675a1f4e96aa4d1bad60d106cd9b98ccebcc664ff05a54e6f9a0de3f |
| services.jar | 33ae2b91bdd1284de98c419f3dba06d0a77ac42fa4413d2ebd1e6c5870ae31c9 |

FACT: OEM policy contains `OplusSmartButtonController`,
`OplusQuickAppController.startQuickApp(boolean)` and
`PhoneWindowManagerExtImpl.oplusPowerPress(long,boolean,int,int)`, alongside
explicit SOS early/late processing. The latter method updates power press count
and schedules sleep; hooking it wholesale would risk single-press semantics.
`isOplusSpecialKey(int)` covers OEM code ranges, not a verified Plus identity.
The presence of SmartButtonController alone does not prove it owns this model's
Plus Key. These are candidate research paths, not activated/validated hooks.

The current upstream API is 102; API 101.0.1 source was inspected at tag commit
`edeb8379c067b16b91af3cb526f5f04db25c06b6`. It uses a no-arg XposedModule,
onModuleLoaded callback, interceptor Chain API and META-INF/xposed metadata.
Do not copy the old API-100 constructor examples.
