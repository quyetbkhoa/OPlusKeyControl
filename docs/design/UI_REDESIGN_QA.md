# UI redesign validation — 2026-10-22

Approved contract: [DESIGN_DIRECTION.md](DESIGN_DIRECTION.md), option A selected by the user. Applied Android UI Art Direction and Android emulator QA skills.

## Result

Dark charcoal/blue utility UI. Two destinations (Keys, Tools), compact master and actual backend status, wrapping physical-key filters, divided rule rows, one-page rule editing with sticky save/draft actions, searchable app/activity lists, grouped tools and matching native overlay styling. Existing capability, conflict, permission and confirmation gates remain in place.

## Scope preservation

43 protected source/build/manifest files match the pre-redesign SHA-256 inventory (domain, key processing, backend, actions, permissions/capabilities, discovery and storage). Inspector snapshot/capture/save implementation is unchanged; only its native overlay styling changed. No dependency or manifest changes. Real phone configuration was backed up before install; rollback APK preserved locally. No automatic physical-key, camera shutter or recording test was performed.

## Actual visual verification

Private screenshots are local ignored evidence under `private-evidence/`, not shipped assets. Real emulator Compose renders were inspected, with a realistic six-rule fixture used only on the emulator.

| Scope | Evidence | Result |
|---|---|---|
| Phone 1080x2400, 420 dpi, font 1.0 | art-final-main-phone.png, art-final-tools.png | Master state, rule list, settings hierarchy and bottom navigation inspected |
| Narrow 320 dp, font 1.5 | art-final-main-320-150.png | Filters wrap; state and actions remain readable |
| Narrow editor, font 1.5, top/bottom | art-final-editor-320-150.png, art-final-editor-bottom-320-150.png | Selectors stack; form scrolls; save/draft reachable |
| Expanded 1200x800 dp | art-final-wide-rail.png | Navigation rail and centered bounded content |
| Schedule/priority expanded | art-final-schedule.png | Controls and footer visible |
| App and Activity search with IME | art-picker-ime.png, art-activity-list.png | Results accessible above keyboard; package metadata readable |
| Permissions unavailable/partial | art-permissions-refined.png | Selected mode distinct from actual access/connection |
| Empty list and English | art-empty-render.png | Empty state and localized content rendered |

Critique/refinement: replaced ambiguous emergency icon with labelled action; made key filters wrap; removed duplicate empty-state CTA; stacked trigger selectors for large fonts; fixed letter-per-line connection status on narrow widths; darkened native startup; guarded test action until target exists. Brand title intentionally ellipsizes at large font on narrow screens; full content description remains available. Light mode is outside requested scope. Font scales above 1.5 and fresh physical button behavior were not verified.

## Build and packaging

`:domain:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -PopcRuntime=true -PopcAdvancedInputs=true`: successful. 166 JVM tests, zero failures; lint zero errors and 85 warnings. Packaging audit confirms runtime/advanced flags, expected Vector scope and compile-only Xposed API.

APK: `artifacts/OPlusKeyControl-art-direction-debug.apk`.
SHA-256: `19e905e2e48b19adec5910eaaaafd9d6010f8f490563cbd92d0b59043523d177`.
Rollback: `artifacts/OPlusKeyControl-before-art-direction-debug.apk`, SHA-256 `bb110e5186355dbf7e6294c1aed5327e987bccabe6e8c0f8abca4b010d21f75b`.

Phone install succeeded; installed APK bytes match the packaged SHA-256. Authorized soft reboot completed: system_server PID changed and kernel boot ID stayed the same. TCP reconnected and the new system_server bound BackendKeeperService. Persisted configuration is byte-identical before/after. Phone remains locked, so final physical UI inspection is pending user unlock; no claim of physical-button validation. No external PowerKey module or unrelated app settings changed.
