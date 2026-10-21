# Device discovery — 2026-10-22

Read-only ADB inspection. No APK installation, settings write, module change,
input injection, process restart, EVIOCGRAB or reboot was performed.
Device serial, installed personal app lists and private logs are intentionally omitted.

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
| FACT | event0–3 device/power/wakeup reports enabled | sysfs read; does NOT prove delivery in deep sleep |
| FACT | Camera com.oplus.camera v6.070.230 | dumpsys package scoped to camera |
| FACT | Stock normal intent -> .component.CameraImageActivity; secure -> .Camera | cmd package resolve-activity with -p com.oplus.camera |
| FACT | Unscoped still-camera intent resolves to ResolverActivity | Other handlers exist; do not trust default resolution |
| FACT | Device Dozing, idle mode false at observation | selected dumpsys power fields |
| UNKNOWN | Runtime modern Xposed API is 101 | User reports 101; module.prop does not advertise API; current upstream master is 102 |
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

Shizuku binder/permission detection is real; Shizuku is not an input-capture API.
Root is opt-in, no automatic su fallback from Basic/Shizuku. Do not replace the
existing power daemon or enable a system_server hook before conflict review.
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

First proposed device test: install this debug APK, open UI, manually grant camera,
microphone/notifications only when testing actions, then optionally enable Basic
accessibility. No Power/Plus hook. Disable from UI/tile before uninstall; verify
volume pass-through on unmatched rules, disconnect and removal. Camera capture,
recording, internal-activity launch and every reboot need their own concrete scope.
For future Vector experiments: validate exact API 101 artifact and firmware method,
document manager disable/recovery before activation. A bootloop-risk experiment is
not authorized by permission to build/install the UI.
