# Approved device action tests — 2026-10-22

The user instructed the agent to perform the needed tests. This expanded the
earlier UI-only approval to the action tests announced in chat: torch toggle,
short visible recording, launcher/picker checks and read-only Plus capture.
No reboot, system restart, SOS, emergency combination, input grab or system hook
activation was performed. Camera/microphone/notification permissions were granted
to this app using scoped `pm grant`; they remain granted.

## Observed results

| Test | Result | Measured evidence / scope |
|---|---|---|
| Wizard button safety | PASS | Selecting Power shows double only; single/long/chord absent |
| App picker/search | PASS | Search OPlusKeyControl returns scanned application; selection works |
| Activity picker | PASS for own package | PM discovers MainActivity, exported=true, launcher classification; no internal/protected launch tested |
| Create/edit scheduled draft | PASS | TimePicker chosen 22:00–06:00, daily, disabled rule saved |
| Persistence after force-stop | PASS | App-only force-stop/relaunch retains 22:00–06:00 rule |
| Persistence after APK update | PASS | Debug update retains rule and runtime grants |
| Torch toggle | PASS, unlocked/on | LED white/yellow brightness 51/51 when on; 0/0 after off; media.camera also logs on/off for app PID |
| Visible recording start | PASS | Caller is app UID in TOP state; microphone FGS type 0x80, notification ID 20, one action; AppOps RECORD_AUDIO running |
| Toggle stop and file publication | PASS | Microphone no longer running, RecorderService absent, MediaStore is_pending=0; 174131 bytes, duration 10681 ms |
| Auto-stop 10 seconds | PASS for timer/service | Own private service requested by root ADB while UI visible with seconds=10; stopped itself; 162262 bytes, duration 9938 ms, is_pending=0 |
| Recording history | PASS | Refresh shows saved test recording in app |
| Stock camera open only | PASS, unlocked/on | Camera service CONNECT device 0 with client com.oplus.camera; no shutter requested |
| Launcher action | PASS for self-target only | In-app Test action confirmation invokes own scanned launcher; MainActivity resumes |
| Plus raw physical event | PASS, screen on | User pressed/released once; event0 BTN_TRIGGER_HAPPY32 DOWN/UP, 238 ms apart |
| Settings scroll regression | FIXED and retested | Stable LazyColumn keys and independent per-tab state; torch button stays at y=1723 for both toggles despite result-message insertion |

The auto-stop test used ADB UID 0 to start the private service; it **does not**
prove a normal app can start recording from the background/lock screen. The first
recording was started through the visible UI as the app UID, separately proving
the while-in-use path. Notification presence was observed; its Stop action and
the visual privacy indicator were not separately exercised/inspected.

The two short test audio files remain in Music/OPlusKeyControl. No recording was
played, uploaded or copied into the repository. Metadata only was queried.
The disabled test rule targets this app, runs 22:00–06:00 daily and remains a
draft; it cannot remap Plus. Existing user-created mappings were absent at the
start of the test. The master switch was already on when testing began; no
input backend was activated by these tests.

## Firmware research and remaining work

Physical Plus now confirmed as Linux key 735 / BTN_TRIGGER_HAPPY32 on event0.
The current dumpsys input assigns gpio-keys.kl, which maps Linux 766 to CAMERA
but contains no 735 entry. An Android KeyEvent/OEM translation is still unknown.
Do not hardcode an Android keycode from the Linux value.

Offline OEM code contains KeyGestureControllerExtlmpl.handleAIKey and
handleAIKeyForFlashMemory with separate long/double processing, plus
OplusKeyEventManagerService.registerKeyEventInterceptor. Their existence is
FACT from a private firmware copy; their ownership of the observed physical
Plus event is still INFERENCE. No observer/interceptor was registered and no
system_server hook was activated. Scoped logcat did not establish that path.

Still NOT_TESTED/UNIMPLEMENTED: real remapping and suppression for every tier,
stock camera readiness/shutter/photo acknowledgement, privileged activity
launch, locked/off/deep-sleep behavior, backend loss/uninstall/reboot and battery
measurements. Passing manual actions does not make this a working remapper.

## Updated artifact

The scroll fix is built, installed and retested. Version remains 0.1.0-dev:

- APK: artifacts/OPlusKeyControl-0.1.0-dev-debug.apk
- SHA-256: 3ecb27451ffb9a04471e80d2664d8714b19cb3a07fc04579151ebbda3d37ea4a
- Bytes: 34,153,584
- Build/lint/JVM tests PASS; 61 tests, lint 0 errors / 14 warnings.

This replaces the artifact described by the initial BUILD_EVIDENCE hash; that
document records the historical first build. Current hash is also in the local
artifact sidecar and build-evidence.json. Source remains on dev, no push/release.
