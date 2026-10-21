# Local build evidence — 2026-10-22

JDK: Eclipse Adoptium 17.0.17.10, SDK platform 36. Clean app build:

```powershell
.\gradlew.bat :app:clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :domain:test --console=plain
```

Result: **BUILD SUCCESSFUL**, 40 seconds for this run. Domain tasks were already
current from the previous run; test XML confirms all suites below passed.

| Suite | Tests | Failed/errors |
|---|---:|---:|
| CameraCaptureTest (contract only, not stock adapter) | 7 | 0 |
| GestureTest | 18 | 0 |
| RuleTest | 16 | 0 |
| ScheduleTest | 13 | 0 |
| RuleCodecTest | 7 | 0 |
| Total | 61 | 0 |

Lint: **0 errors, 14 warnings**. Warnings include intentionally pinned older
dependency/toolchain versions, API 36 target rather than a newer target, and
localeConfig ignored below API 33. QUERY_ALL_PACKAGES has one narrow manifest
suppression with core sideloaded-picker justification; no global lint baseline
or abortOnError override. Gradle reports bundled native libraries could not be
stripped and packages them intact. No runtime assertion follows from lint.

`apksigner verify --print-certs` succeeded. Android Debug signer SHA-256:
`9895724767e880d6726103fbb0f5784452829b7a040b822fb18c94fc06fdf879`.
No custom secret signing material was created or committed.

Audited deliverable: `artifacts/OPlusKeyControl-0.1.0-dev-debug.apk`

- Bytes: **33,767,704**
- SHA-256: **9ed786be25668966aa98d76468c5787af95a3e660928d899fd9d120dc865951f**
- App ID: `dev.opluskeycontrol`, version `0.1.0-dev`, code 1, min API 28 / target 36
- `tools/package_debug.py` checked Xposed metadata/scope and DEX class definitions:
  zero bundled libxposed API implementation classes, self-scope probe exists.
- Permission inventory includes camera/microphone/notification/FGS/package
  discovery/Shizuku; **no INTERNET or WAKE_LOCK permission**.

APK/hash/JSON evidence are ignored local artifacts, not committed release files.
CI is prepared but has not run on GitHub. No remote push, tag, release or merge
to main was performed. Device installation/UI testing is pending USB reconnection;
no camera/recording/physical-input result is claimed.
