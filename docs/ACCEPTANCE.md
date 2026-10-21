# Acceptance status — 2026-10-22

**Development checkpoint, not complete product acceptance.** A successful build,
JVM test or bytecode inspection is not physical-device input/action evidence.

Current follow-up: [POLICY_PROBE.md](POLICY_PROBE.md) confirms physical Plus
780/735 DOWN/UP reaches IMS. [RUNTIME_BACKEND.md](RUNTIME_BACKEND.md) describes the
implemented experimental bridge/ownership/actions and the next concrete device
experiment. All replacement, scope revocation, helper cleanup, camera shutter,
background recorder and screen-state acceptance cells remain NOT_TESTED. Partial
Plus mapping and Volume/Power/chord replacement are still unavailable; a runtime
candidate is not acceptance of the full supplied brief.

Local implementation check: 100 JVM tests pass, lint reports 0 errors / 22
warnings, normal/runtime builds and three-mode DEX/signature audits pass. These
are implementation checks, not additional PASS rows for physical remapping.

Update: [DEVICE_ACTION_TESTS.md](DEVICE_ACTION_TESTS.md) supersedes the initial
NOT_TESTED entries for manual unlocked torch, visible recorder toggle/autostop,
stock camera open-only, self-launch, picker/wizard and persistence. Actual
remapping/capture screen-state cells below remain NOT_TESTED.

Latest backend work: POLICY_PROBE.md describes a separate, unactivated policy
observation APK and 15 new gate tests. Total JVM count is 76 PASS. This adds no
physical remapping acceptance result.

## Phases

| Phase | Status | Scope/evidence |
|---|---|---|
| 0 discovery | PASS for completed read-only commands; UNKNOWN remains | DEVICE_DISCOVERY.md; no writes to phone |
| 1 engine/storage/UI/build | PASS build/JVM/lint; UI NOT_TESTED pending ADB | Compose source, versioned storage, unit reports |
| 2 Basic | PARTIAL | Read-only Volume observation, manual public-API actions; replacement intentionally gated |
| 3 Shizuku | PARTIAL | Binder/permission integration; capture adapter not implemented |
| 4 Root/Vector | PARTIAL / BLOCKED activation | Opt-in identity check, API 101 zero-hook probe; no physical policy backend |
| 5 actions | PARTIAL | Torch, launcher, visible recorder implemented; stock capture and privileged activity launch unverified |
| 6 stabilization | PARTIAL | CI/docs/license/signature checks; runtime battery/reboot/uninstall not tested |

## Requirement matrix

| Requirement | Status | Evidence/limit |
|---|---|---|
| Plus single/double/long | NOT_TESTED / replacement gated | Pure gesture tests only; physical keycode unknown |
| Vol+ single/double/long | NOT_TESTED / replacement gated | Engine tests; Accessibility observes only |
| Vol- single/double/long | NOT_TESTED / replacement gated | No event consumption |
| Power double | NOT_TESTED / replacement gated | Domain accepts double, no policy hook |
| Power single/long untouched | PASS static invariant; runtime NOT_TESTED | Domain rejects both; shipped source never intercepts Power |
| Plus+Vol+, Plus+Vol- | PASS engine semantics; runtime NOT_TESTED | Held primary/reverse simultaneous/debounce/cancel tests |
| Power chord | Unavailable pending safety proof | Domain rejects every Power chord; no SOS/screenshot experiment |
| Unassigned gesture pass-through | PASS static Basic; runtime NOT_TESTED | Basic always returns false, no grabbing/injection/hook |
| Multiple windows/default/priority | PASS JVM | Boundary, midnight, weekdays, overlaps, timezone, DST |
| Action torch | IMPLEMENTED; runtime NOT_TESTED | CameraManager callback + permission/error handling |
| App launch | IMPLEMENTED; runtime NOT_TESTED | Scanned launcher + locked-screen refusal + exception handling |
| Activity discovery | IMPLEMENTED; runtime NOT_TESTED | Per-package PM scan; protected/internal disabled |
| Root privileged activity | BLOCKED / not implemented | No target UID/signature claim; no permissive bypass |
| Stock camera opens/captures/keeps open | BLOCKED / adapter not implemented | Normal/secure activities resolved read-only; coordinator only JVM-tested |
| Visible recorder toggle/autostop | IMPLEMENTED; runtime NOT_TESTED | Microphone FGS, Stop notification, MediaStore pending publication |
| Schedule-independent recording Stop | PASS logical invariant; runtime NOT_TESTED | UI/notification Stop + RecordingContinuation unit test |
| Backend ownership/no duplicates | PASS domain; integrations NOT_TESTED | SourceOwnership, foreign-source and repeat tests |
| Kill/disable/uninstall restoration | NOT_TESTED | No runtime hook/daemon/settings write; not claiming verified uninstall |
| Idle CPU/battery/memory/wakeups | NOT_TESTED | No fabricated measurements; no permanent FGS/wakelock/polling in app |
| Picker-only setup, locale/dark/TalkBack | IMPLEMENTED; runtime NOT_TESTED | Resource parity/static checks; visual/TalkBack awaiting UI test |
| Build/debug signature | PASS | Gradle assemble + apksigner verify; Android Debug signer |
| Backup roundtrip/schema/size/safety | PASS JVM | Seven RuleCodec tests; restore forces disabled |

## Physical input screen-state matrix

Every cell is **NOT_TESTED**. Hook/source activation is **BLOCKED** until explicit
approval and a proven firmware adapter exist. Approval to install and test UI
does not authorize any cell below. P+V means hold Plus then Volume; reverse order
is treated as simultaneous only inside chordMs.

| Trigger | Unlocked/on | Locked/on | Off/immediate | Idle/deep sleep | After reboot |
|---|---|---|---|---|---|
| Plus single | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Plus double | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Plus long | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Vol+ single | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Vol+ double | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Vol+ long | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Vol- single | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Vol- double | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Vol- long | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Power double | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Power single (preserve) | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| Power long (preserve) | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| P+V+ | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |
| P+V- | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED |

## Device/UI approval and current blocker

User explicitly approved installing `dev.opluskeycontrol` debug APK and testing UI,
with no accessibility/Vector activation, permission grants, remapping, recording,
photos or reboot. Before installation, `adb devices -l` returned an empty list.
The install has **not** been attempted against an unknown/missing target.
User has been asked to reconnect USB; permission remains valid when it returns.
Update: user reconnected over TCP and requested installation. Install, cold start
and initial Vietnamese dark Overview rendering now PASS; see
[DEVICE_UI_CHECK.md](DEVICE_UI_CHECK.md). Other UI flows remain NOT_TESTED.

Emulator attempts: existing API 36 AVD booted but ADB remained unauthorized. A
separate temporary AVD could not start because the google_apis image contained
only incomplete installer files and lacked kernel-ranchu. A subsequent emulator
preparation command was rejected by automatic tool review (`blocked by policy`).
No global adb kill-server or physical-device reboot was used to work around it.

## Next approved UI test

Verify installed package version/signature, open app, inspect Overview/Mappings/
Settings, create/edit/delete disabled schedule drafts, test app/activity picker
without launching targets, check normal/dark/large text only if those settings can
be changed per app without changing device-wide preferences. Capture private UI
evidence and redact before publishing. Do not press manual action test buttons.

Subsequent approvals: camera/microphone permissions + visible action tests;
read-only physical Plus capture; self-scope Vector probe; only then a separately
reviewed narrow policy experiment with independently tested rollback. No reboot
approval is implied by any earlier installation approval.
