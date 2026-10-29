# Grouped keys, action categories and triple press

2026-10-25. Refinement requested directly by the user, following the approved
native utility direction in DESIGN_DIRECTION.md.

## Delivered UI

- Separate Open app and Open Activity categories. App mode lists launchable
  apps and returns the selected launcher target; Activity mode opens an app's
  Activity list. The editor no longer relies on a small Screens shortcut.
- One container per key/group with horizontal dividers. Plus shows single,
  double, triple and long press; Power shows double and triple. Unassigned slots
  stay visible and open the editor directly. Existing mappings are retained.
- Button service replaces the Plus-specific connection name. Connected uses
  green, disconnected red, checking amber; permission indicators follow the
  same semantic colors. Dashboard says Button shortcuts with Active, Paused
  or Waiting for connection, based on both the master setting and real service.
- Removed the stale Unsupported badge from the chord timing control.
- Compact typography: 20sp screen title, 15sp row title, 13sp supporting text,
  12sp metadata. Controls retain minimum 48dp targets. Connection status moves
  below its title to avoid squeezing text at large font scales.

## Gesture behavior

TRIPLE is an appended serialized enum value. Existing names/configurations are
unchanged. Plus counts three physical clicks with no single/double duplicate;
double waits one extra multi-press window only when a triple rule is enabled.
Single still uses one multi-press window. Long/cancel/chord logic stays active.

Power triple uses the existing exact-firmware OEM semantic callback and raw
physical-edge observer, with a distinct capability bit. Only ordinary completed
counts 2/3 can dispatch; counts 4+ are passed through. No raw Power suppression,
injection or automated physical/SOS gesture tests were performed. Actual Power
behavior still depends on the ROM compatibility guard and requires user testing.

## Verification

198 JVM tests passed, including triple recognition, double fallback timing,
single timing, long-third cancellation, expired third click, Power raw-edge and
semantic callback requirements, higher-count pass-through, and JSON round trip.
Runtime/advanced-input assemble and lint succeeded: 0 errors, 124 warnings.
DEX/scope/build-flag audit and APK v2 signature verification passed.

Emulator API 36 (OplusMd3Qa, emulator-5556) rendered and inspected at 1080x2400,
420dpi (~411dp wide), normal font scale; and 840x1860, 420dpi (320dp wide),
200% font scale. Tested grouped mappings with assigned/unassigned slots and
disconnected/missing-permission state; editor; separate app and Activity flows;
and timing page without the obsolete badge. Emulator-only sample mappings were
used. No target action was executed during picker navigation or saved into the
physical phone as a fixture.

Visual iteration corrected the connection status chip squeezing its title on
the 320dp/200% layout; status is now below the title. Screenshots and UI trees:
private-evidence/grouped-ui-20261025/ (not committed).

APK: artifacts/OPlusKeyControl-grouped-triple-debug.apk.
SHA-256: e2cfadde1ea6295a2785d8beb85625f705afbd31bc10769846f4be6b737c9ebb.

## Physical-device deployment

Installed with adb install -r on CPH2745 / CPH2745_17.0.0.102(EX01), then one
Zygote soft restart. The installed APK hash matches the artifact. DataStore
configuration remained byte-identical. system_server PID changed 20632 -> 21141,
kernel boot ID stayed unchanged. Service dump confirms connected=true,
authorized=true, enabled=true and torchReady=true after initialization.

New triple slots are unassigned; no physical triple gestures or target actions
were triggered automatically. The user's phone was in active use afterward,
so final UI inspection used the emulator rather than interrupting that use.
