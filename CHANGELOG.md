# Changelog

## v1.0.3 — 2026-10-29 (runtime preview)

- Require configuration-applied acknowledgement before reporting the root
  backend connected; recover a stalled handshake with timeouts and backoff.
- Continue recovery after prolonged app/root-watcher restart failures.
- Serialize actions through a bounded FIFO; expire pending requests after five
  seconds and revalidate authority and rules immediately before execution.
- Keep executor busy state separate from published action readiness. Preserve
  pending Plus/Power gesture recognition during unchanged policy refreshes.
- Retry transient camera disconnect/error with the same desired torch state.
- Add triple press for Plus and Power, grouped key controls and separate app
  and Activity selection. Retain only the three Plus combinations; migrate
  removed Power/Volume mappings and suppress the Plus/Power single-Power callback.
- Validation: 218 JVM tests passed; build/lint and APK signature/scope audits
  passed. App and root-watcher kill tests recovered without opening the app.
- Physical first-press behavior after prolonged idle remains unverified.
  A press before initial authorized configuration is applied can still pass
  through to the OEM. This release remains experimental.
- The APK is the exact tested debug build, with the existing local debug signer,
  manifest versionName `0.1.0-dev` and versionCode `1`. The release tag identifies
  the source snapshot; activation requires reloading the module and restarting
  Android after an update.

## v1.0.2 — 2026-10-25 (runtime preview)

### Fixed

- Automatically reconnect the system hook and app after app process death,
  service disconnection, binding death, failed HELLO, or action-channel failure.
  Reuse a bounded retry window and reset it after valid configuration arrives.
- Serialize connection recovery on the policy worker and ignore stale service
  callbacks, so an old connection cannot disconnect its replacement.
- Evaluate Activity/app readiness per mapping. One unverified target no longer
  blocks unrelated mappings that use the same action kind.
- Support the inventoried CPH2745 ColorOS 17/API 37 framework profile, including
  its Power gesture entry point and native Power/Volume combination classes.
  Retain the inventoried ColorOS 16/API 36 profile; reject unknown firmware.

### Added

- Optional recognition vibration, including one pulse at the long-press
  threshold while the button remains held and duplicate-feedback suppression.
- Power double press, bidirectional Plus/Volume combinations, Plus-first
  Plus/Power and native Power/Volume combinations in the advanced runtime build.
- Grouped mappings, app/Activity selection, utilities and screen inspector in
  the redesigned Vietnamese/English Material 3 interface.

### Changed

- Remove time-window scheduling from the mapping editor and persistence.
  Migrate legacy scheduled rules while retaining actions and resolving enabled
  duplicates; preserve gesture timing and recording auto-stop settings.
- Keep the app-based action executor. The proposed system-only executor is not
  included in this release.

### Validation and limitations

- 182 existing JVM tests passed. Lint: 0 errors, 118 warnings. APK DEX/scope/build
  flag audit and APK v2 signature verification passed.
- Installed on CPH2745 / CPH2745_17.0.0.102(EX01). APK hash matched and configuration
  remained byte-identical. After a deliberate app-process kill, the backend
  reconnected from UID 1000 without reopening the Activity or pressing a key.
- The recovery test does not prove every physical gesture, deep-sleep behavior,
  tactile feedback or zero latency. Runtime features remain experimental.
- The release asset is the exact installed debug APK. Its Android manifest
  retains versionName `0.1.0-dev` and versionCode `1`; `v1.0.2` identifies the
  source/release snapshot. It uses the existing local debug signer.

## v1.0.1 — local snapshot

Local working baseline with independently mapped Plus gestures, MP3 recording
and setup UI. This tag existed locally before the first GitHub publication.
