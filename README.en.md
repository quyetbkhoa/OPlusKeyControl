# OPlusKeyControl

Android 16 / OnePlus 15 time-aware button utility. **0.1.0-dev does not remap
physical buttons yet.** See [acceptance status](docs/ACCEPTANCE.md),
[device evidence](docs/DEVICE_DISCOVERY.md) and [recovery](docs/RECOVERY.md).

The approved observation probe captured physical Plus keyCode 780 / scanCode 735.
A separate [experimental runtime candidate](docs/RUNTIME_BACKEND.md) now connects
the hook, authenticated app transport, gesture/rule engine and action dispatch.
It has been installed for a UI check, but is not device-accepted. Raw Plus ownership requires active
single/double/long coverage together; partial mappings pass all input through.
Normal builds remain self-scoped with zero system hooks. Build the candidate with
`-PopcRuntime=true` and audit/copy with `python tools/package_debug.py --runtime`.

The summary below describes the normal build and previous manual device tests.
Runtime camera, background recording, internal Activity targets, scope revocation
and helper cleanup still require explicit device acceptance.

Implemented: pure Kotlin gesture/rule engine, schedules with overnight weekdays,
priority/conflict detection, Compose Material 3 wizard, Vietnamese/English,
versioned DataStore and validated backup/restore, app/activity discovery,
manual torch and visible-UI microphone FGS recording, Stop notification, history,
read-only Volume diagnostics, real Shizuku permission detection and opt-in root
identity probe. The API 101 module entry is a **zero-hook self-scope probe**.

Not implemented/verified: OEM replacement, Plus/Power capture, Shizuku input,
native root listener, system_server hook, deep sleep, privileged activity launch,
stock camera readiness/shutter/photo acknowledgement, lock-screen recording,
multi-profile launch. Unsupported mappings remain disabled drafts.

Build with JDK 17 and Android SDK 36:

```sh
./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Set `ANDROID_HOME` or an ignored `local.properties`. APK:
`app/build/outputs/apk/debug/app-debug.apk`. No signing secrets are committed.
The repository has no remote; CI is prepared but has not run on GitHub.

Only install/enable device features after approval. Accessibility observes Volume
and always passes events through; no window content access. Shizuku does not
fall back to root. The Vector probe needs no system scope and does not remap.
Manual action buttons perform real actions. Microphone recording starts from a
visible app with runtime permission, Android indicator and Stop notification.
Android 10+ recordings live in `Music/OPlusKeyControl`; Android 9 uses app storage.

Power single/long and Power chords cannot be configured. No EVIOCGRAB, injection,
permanent keylayout/OEM settings changes or helper daemon. Emergency disable
latches immediately; restore always disables remapping. Physical-device
kill/uninstall/reboot behavior is not yet acceptance-tested. Public recordings
are retained after uninstall.

`QUERY_ALL_PACKAGES` is needed for this sideloaded utility's app/activity picker.
Discovery stays in memory; no INTERNET permission, analytics or uploads. Current
profile only. Export includes selected package/activity IDs. Private firmware
evidence is excluded from Git. Apache-2.0; see third-party notices.
