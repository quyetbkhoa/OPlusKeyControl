# OPlusKeyControl

Kotlin/Compose hardware-button utility for OnePlus 15, with Vietnamese and
English UI. The experimental Vector/libxposed runtime recognizes input in
`system_server` and dispatches actions to the app over a UID-authenticated Binder.

[Tiếng Việt](README.md) · [Changelog](CHANGELOG.md) ·
[v1.0.2 release notes](docs/releases/v1.0.2.md) · [Recovery](docs/RECOVERY.md)

Features include independent Plus single/double/long mappings, torch control,
app/Activity selection, camera actions and MP3 recording subject to permissions
and readiness checks. The advanced candidate adds Power double press,
bidirectional Plus/Volume, Plus-first Plus/Power and native Power/Volume chords.
The Material 3 interface groups mappings by button and includes utilities,
an inspector and optional recognition vibration. Time-window scheduling has
been removed; legacy schedules are migrated without discarding actions.

## Runtime compatibility

Only the inventoried `CPH2745` profiles are accepted:

- API 36: `CPH2745_16.0.10.601(EX01)`.
- API 37: `CPH2745_17.0.0.102(EX01)`.

The current APK is a **runtime preview**. There are 182 passing JVM tests and
zero lint errors (118 warnings). On the ColorOS 17 device, installation and
configuration preservation were verified; after killing the app process, its
backend rebound from UID 1000 without reopening the Activity or pressing a key.
This does not prove every physical gesture, deep sleep, tactile feedback or
zero latency. See the dated verification documents linked from the Vietnamese
README for historical details.

Power single/long and the two-Volume chord remain reserved. Standalone Volume
replacement and Shizuku input are unavailable. No input replay, keylayout
replacement or OEM key-setting writes are used.

## Build

Use JDK 17 and Android SDK 36. Configure `ANDROID_HOME` or an ignored
`local.properties`.

```sh
./gradlew -PopcRuntime=true -PopcAdvancedInputs=true :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python tools/package_debug.py --runtime --advanced-inputs
```

Omit both flags and the audit options for the normal self-scoped build with no
system hooks. CI checks normal, Plus runtime and advanced runtime variants.
The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Installation and privacy

Runtime requires a compatible modern libxposed API 101 framework with system
hook support and module scope `system` plus `dev.opluskeycontrol` for user 0.
Open the app, grant required permissions and check the backend before enabling
mappings. Updated system hooks require a Zygote restart or device reboot.

The v1.0.2 asset is the exact debug APK installed and tested on the device. Its
manifest retains versionName `0.1.0-dev` and versionCode `1`; the release tag
identifies the source snapshot. It retains the existing local debug signer.
A build made on another machine can have a different signer and may not update
an existing installation in place.

No INTERNET permission, analytics or ads. Package discovery serves the
app/Activity picker. Recording uses a microphone foreground service with a Stop
notification and stores files in `Music/OPlusKeyControl` on Android 10+.
Signing keys, private device/configuration evidence and the incomplete
system-only executor draft are excluded from source and release assets.
Apache-2.0; see [third-party notices](THIRD_PARTY_NOTICES.md).
