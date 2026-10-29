---
design_direction_version: 2
status: approved
product_name: OPlusKeyControl
platforms: android-phone
visual_personality: native_utility
visual_intensity: balanced
color_strategy: brand_accent
typography: system_native
density: compact
shape_language: rounded
motion: subtle
appearance: dark_first
approved_by: user
---

# Design Direction — OPlusKeyControl

## Approval and source

Updated 2026-10-24 following the user's explicit request to implement
MD3_Utility_Reference_Kit_v2.zip without a design interview. The attached
Android_MD3_UI_Playbook_v2.txt (internal version 2.1) and reference images
provide visual guidance; their sample networking content is not app scope.
Product name and existing backend behavior remain unchanged.

## Information architecture

- Gán phím: master enable state, actual connection entry, Plus, Power, Volume,
  and combination groups. No Plus-focused dashboard or key filter carousel.
- Tiện ích: recordings, screen inspector, action tests.
- Settings gear: connection and permissions, background operation, timing,
  backup, diagnostics and about.
- Add: choose a supported trigger, choose action, save. Editor combines trigger
  and action in one group; priority expands under advanced options.
- App picker: tap app to launch it, or choose its Screens button for Activities.
  Activity launch verification is retained; unsupported targets stay gated.
- One Save button. If readiness is missing, explicitly indicate saving disabled.
  Existing mappings retain their priority, action and enable controls.
  Time-window activation was removed at the user's request on 2026-10-24.
  Legacy schedules are migrated to always-on mappings, retaining inactive
  duplicate actions as disabled entries when necessary.
- Delete remains in the editor with confirmation. Help moves to contextual info.

## Visual tokens

Black #000000 canvas; charcoal #1A1A1A groups; #242424 inset surfaces.
Primary text #E8E8E8, secondary #A0A0A0, blue links #8AB4F8.
Filled Save #1458C0/white; active switches #247DFF/white.
Selected surfaces #20364F with #CDE1FF text; outline #717171,
dividers #333333, error #FFB4AB, success #8ED6A8, warning #EAC36B.
16dp gutters and group radius; minimum 48dp interactive targets.
System font: titles 22/28sp, rows 17/24sp, support 15/22sp, metadata 13/20sp.
Flat groups, no decorative hero or repeated technical paragraphs.
Vietnamese primary with English resources; native dialogs and bottom sheets.

## Validation boundary

Compile, JVM tests, lint and emulator UI checks are recorded separately in
MD3_IMPLEMENTATION_QA.md. Emulator rendering does not establish physical-key
capture or ColorOS 17 hook correctness. The existing runtime compatibility and
per-rule readiness work is carried forward, without changes by this UI task.

## Compact refinement — 2026-10-25

User requested tighter spacing and slightly rounder components. Cards use 20dp corners, 12dp internal padding and 8dp list gaps. Rows retain at least 48dp touch targets; utility entries use horizontal rows instead of tall tiles.

## Grouped controls refinement — 2026-10-25

User requested app launch and Activity launch as distinct action categories;
Activity selection starts with an app list and then its Activity list, with no
small Screens shortcut. Every Plus gesture (single, double, triple, long) and
Power double/triple remains visible, including Not assigned slots. Each key
uses one container with horizontal dividers. Actual connection states use green
for connected, red for disconnected and amber for checking. Header uses Button
shortcuts with Active/Paused state. Compact type: title 20sp, row 15sp, supporting
13sp, metadata 12sp; maintain minimum 48dp touch targets.
