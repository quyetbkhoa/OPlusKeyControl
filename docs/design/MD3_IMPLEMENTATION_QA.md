# MD3 Utility implementation QA

Date: 2026-10-24. Product: OPlusKeyControl.

## Delivered behavior

- Gán phím and Tiện ích are the two primary destinations; settings uses a gear.
- Mappings are grouped Plus, Power, Combinations, then Volume. Each mapping has
  a direct edit target and independent enable switch. Unsupported standalone
  Volume mappings remain explicitly unavailable.
- Trigger selection is one supported-choice sheet. The editor has one Save
  button, action selection, optional schedule/priority and delete confirmation.
- App selection combines opening an app with choosing one of its Activities.
  Default discovery includes launchable system apps; extra system packages are
  opt-in. Package queries run off the UI thread. Technical Activity details are
  reduced, with full identities available through advanced disclosure.
- Existing readiness checks remain. Unverified Activities save disabled with a
  visible explanation. Saving does not execute the action.
- Black/charcoal groups, blue controls, native typography and 16dp corners follow
  the supplied kit. No sample networking functionality or figures were imported.

## Build and automated checks

Command: `gradlew.bat -PopcRuntime=true -PopcAdvancedInputs=true
:app:assembleDebug :domain:test :app:testDebugUnitTest :app:lintDebug`.
The initial run compiled and passed both test suites; subsequent UI fixes were
rebuilt and linted. Final result: build successful, 171 JVM tests with zero
failures/errors, lint zero errors and 120 warnings. Warnings are retained in
app/build/reports/lint-results-debug.html; this is not a warning-free build.

The existing packaging auditor was run with MD3-specific output names. It
verified the intended system/own Vector scope, runtime/advanced-input build
flags, inert disabled policy-probe path and compileOnly libxposed boundary.
APK signature verified with apksigner (v2 Android debug signer).

Artifact: `OPlusKeyControl-md3-utility-debug.apk`
SHA-256: `d3d0e992001818860274d706cb1ca147045b4e2f33f10baac5052aa4083af55e`

## Emulator evidence

Android API 36 x86_64 emulator, dedicated OplusMd3Qa AVD. Only the emulator was
installed/modified during this UI task. Test mappings were synthetic fixtures,
not a copy of the phone's configuration. Root backend was disconnected; no real
hook or physical-key capability is implied by the screenshots.

Inspected native UI hierarchy and screenshots at:

- 1080x2400, density 420, default font (about 411dp wide), Vietnamese.
- 840x1680, density 420, font scale 1.5 (320dp wide): labels wrap, editor scrolls
  and Save remains reachable in the bottom bar.
- 2100x1575, density 420, default font (800dp wide): navigation rail and bounded
  content render without overlap.
- English empty state was also inspected before setting the app locale to vi.

Flows exercised: home with existing mappings; add Plus hold; direct app
selection and save; Activity selection and disabled save without proof; expand
schedule/priority; delete with confirmation; Utilities; settings; recordings
empty state; inspector permission state. Screen transitions used fresh
uiautomator bounds rather than guessed screenshot coordinates.

Evidence is under `private-evidence/md3-kit-v2/`: final-home.png,
final-editor.png, final-utilities.png, settings.png, apps-default.png,
activities.png, final-activities.png, final-activity-selected.png, activity-editor.png, saved-activity.png, after-delete.png,
small-large-font.png, small-editor.png and wide-home.png, plus XML trees.
Some intermediate screenshots precede final small spacing/text refinements;
final-* files show the final layout. Runtime source bytes in the installed
emulator build differ only by final newline cleanup from the packaged APK.

## Preservation and limits

Compared hashes for all protected app non-UI/non-resource source and all domain
source against the snapshot taken before this UI work: no changes. Existing
ColorOS 17 compatibility and per-rule readiness changes from earlier work are
included in this APK and were not altered by the UI task. No physical-phone
install, reboot, configuration migration, or button test was performed here.

Real Plus double-press, Plus+Volume+, lock-screen behavior, recorder capture,
and ColorOS 17 runtime still require physical-device verification. Emulator
visual/flow checks do not establish those outcomes.
