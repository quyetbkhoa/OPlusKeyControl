# Power gesture / action vibration update

2026-10-24. User reported Power double pressing only switches the screen off,
and requested a vibration setting to confirm actions.

## Implementation

Exact ColorOS 17 services.jar shows PowerKeyRule.onKeyGesture(SingleKeyGestureEvent)
as the public semantic dispatch entry point; onMultiPress(JII) is now a private
helper invoked by that entry point. API37 now hooks the public entry point and
accepts only ordinary (type0), completed (action1), physical two-press events.
Long, very-long, cancellation and unknown semantic events clear candidates and
stay with the OEM. Higher press counts remain OEM owned. API36 retains its prior
callback. No raw Power suppression/replay, global early-press setting write or
emergency callback replacement was introduced. Existing early-short-press guard
remains fail-closed. Bounded raw/callback/mode diagnostics support device tests.

Added persisted hapticFeedback (default true for legacy configurations), with a
Cài đặt > Rung xác nhận switch. App opening, Activity, stock camera and accepted
torch commands request one 35ms confirmation. Recording pulses only once actual
AudioRecord start succeeds and when an explicit stop completes a valid recording.
Failed/rejected/busy requests and automatic timeouts do not emit success pulses.
The app uses VIBRATE normal permission and hardware feedback vibration attributes
on API33+, with an AudioAttributes fallback on API28-32. System vibration policy
is respected; no bypass flags are set.

## Validation so far

179 JVM tests passed, build successful, lint zero errors / 116 warnings.
Added regressions for API37 ordinary completed vs start/long/very-long/cancel,
higher native counts, and legacy/default/off vibration persistence.
The API36 emulator settings switch was turned off, the app force-stopped and
reopened, then the setting restored on; all mappings and detection timing were
preserved. See settings-vibration-*.png and emulator-feedback-result.json under
private-evidence/power-haptics/. Emulator QA does not prove tactile or Power
behavior on a physical ColorOS device.

APK: artifacts/OPlusKeyControl-power-haptics-debug.apk
SHA-256: ee4543858df70d8ec847eda3d4098d0c8dc1128e094c643cc5038af0a87db6ad
Packaging/DEX audit and v2 signature verification passed. Device installation
returned Success; one soft reboot was issued under the user's standing permission
verified in the preceding turn. Device readback/testing is recorded below when
complete.

## Device readback

Installed base.apk SHA matches delivered APK: ee4543858df70d8ec847eda3d4098d0c8dc1128e094c643cc5038af0a87db6ad.
System_server PID changed 14886 -> 32465, kernel boot ID
unchanged. BackendKeeperService was bound by UID1000 after reconnect.
All 5 mappings were unchanged immediately after installation/reload.
Legacy haptic setting defaults to enabled; no existing mapping was rewritten.

While the user was interacting with the app, system logs recorded a 35ms
HARDWARE_FEEDBACK request attributed to dev.opluskeycontrol, with flags=0.
This establishes an app vibration request reached VibratorManager; it does
not establish that the user felt it or that Power double launched the camera.
Physical Power double/camera and tactile confirmation were requested from the
user; the response is pending. No camera shutter, recording or synthetic
Power events were initiated by the assistant.
