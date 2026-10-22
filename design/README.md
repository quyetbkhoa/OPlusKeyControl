# Dark UI references — 2026-10-22

The user supplied ten dark UI images and then requested **dark mode only, code
first**. `references/` preserves those originals. `generated/` contains nine
supplementary dark designs: battery, recordings, timing, backup, manual actions,
diagnostics, clean review, component sheet and state sheet. No light design is
part of this implementation. Images are design references, not application assets.

## Implementation rules overriding illustration text

- Plus gestures work independently. Power/Volume/chords remain unsupported.
- Power single/long and Power chords never become selectable supported remaps.
- Priority remains 0–100, not 1–10 as drawn in the supplied schedule image.
- At 14:37 a 06:00–22:00 rule is in schedule unless its selected day excludes it.
- Permissions, selected mode, backend connection and master switch are separate.
  A missing notification/camera permission must not be called fully granted.
- No barcode scanner, voice commands, fake event history, waveform or elapsed
  recording timer is introduced. App Activity classes are discovered on-device,
  never copied from fictional examples in the images.
- Backup uses Android document pickers; no cloud backup is implemented.
- The recorder folder remains fixed. A folder label does not imply a new picker.
- Notification styling remains native Android. A design-sheet preview cannot
  change system-owned notification layout.

The main app is rebuilt with native Compose components and vector drawing, not
by placing screen-size raster images behind invisible buttons. The dark semantic
palette is in `OpDesign.kt`; values are implementation tokens inspired by these
references, not a claim of exact pixel sampling. Small-screen usability and font
scaling take precedence over reproducing dense illustrations literally.
