---
design_direction_version: 1
status: approved
product_name: OPlusKeyControl
platforms: android-phone
visual_personality: native_utility
visual_intensity: balanced
color_strategy: brand_accent
typography: system_native
density: balanced
shape_language: moderately_rounded
motion: subtle
appearance: dark_first
approved_by: user
---

# Design Direction — OPlusKeyControl

## Approval and source

Approved 2026-10-22. User explicitly selected option A: charcoal background,
blue accent, readable system typography, balanced density, moderate corners and
subtle motion. User requests a new information architecture and layout from
scratch, preserving all logic. The prior green mockups are historical, not a
reference for this redesign. Dark mode only, Vietnamese primary / English.

## Product brief

A physical-key utility: see which rules are configured, change a mapping quickly,
and understand connection/permission failures without reading diagnostic prose.
Three adjectives: precise, calm, practical. First glance: master state, selected
physical key, its mappings. One primary action: add/edit a mapping.

## Visual system

Background #101216; surface #181C22; raised #222832; outline #303844.
Accent #82B3FF; strong accent #4B8EFF; on-accent #071C38; main text #F1F3F7;
secondary #A8B2C1. Success #86D5B1, warning #E9BD76, error #FF969B.
System sans: headline 28/34 semibold, title 20/28 semibold, row 15/21 medium,
body 14/21, secondary 12/18; allow font scaling and Vietnamese diacritics.
Spacing 4/8/12/16/24/32 dp; radius 8/12/16 dp. No gradients, neon, oversized
icons, colored icon carpets, nested decorative cards or permanent helper prose.
Material outlined icons. Rows use dividers, compact status with text and one
clearly scoped control. Motion limited to subtle standard platform feedback.

## Navigation and interactions

Two top-level destinations: Keys (master + compact connection + key filters +
actual rule rows); Tools (grouped utility/settings rows). No duplicate Overview.
Rule editing is one scrollable form: trigger, action, optional schedule/priority,
validation/test and enable state; save/draft at the bottom. App/activity pickers
are searchable dense lists, not card walls. Connection detail distinguishes
selected mode, actual privilege and input connection.

## Engineering and adaptive behavior

Jetpack Compose. Phone minimum 320 dp, center content max 840 dp on wide windows;
rail instead of bottom navigation when width permits. Insets/IME handled by
Scaffold plus bottom-safe form actions. 48 dp targets, wrapping primary content,
no hard heights on dynamic text. Secondary metadata may truncate only where an
edit/details route exposes the full value. No functional changes to domain,
capability gates, RuleStore, Binder, Vector, root actions or recording.

## References and validation

No visual dependency on prior user-supplied green designs. Repository code used
only as the functional inventory. Validate real Compose screens on Android
emulator and, if available, the approved phone; record actual sizes/states and
screenshot critique in UI_REDESIGN_QA.md. Actual render matrix, critique and build evidence are recorded in UI_REDESIGN_QA.md.
