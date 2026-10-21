# Physical-device install and initial UI — 2026-10-22

User explicitly approved debug APK installation and UI checking, then reconnected
the CPH2745 over TCP. Device IP/ADB serial and screenshots remain private.

| Check | Result | Evidence |
|---|---|---|
| Intended device connected | PASS | ADB state device; ro.product.model CPH2745 |
| Artifact integrity | PASS | SHA-256 9ed786be25668966aa98d76468c5787af95a3e660928d899fd9d120dc865951f |
| Installation | PASS | adb install -r returned Success |
| Version | PASS | dev.opluskeycontrol, versionCode 1, versionName 0.1.0-dev, target 36 |
| Cold start | PASS | am start -W Status ok, COLD, TotalTime 399 ms; not a benchmark |
| Foreground activity | PASS | topResumedActivity is dev.opluskeycontrol/.MainActivity |
| Overview renders | PASS | Screenshot inspected: Vietnamese dark UI, master disabled, Basic, Accessibility disconnected |
| Camera/microphone/notifications | Not granted | dumpsys package reports granted=false for all three |
| Other screens/editor/picker | NOT_TESTED this check | No navigation interaction performed |
| Physical remapping/actions | NOT_TESTED | No hook, permission grant, key test, photo, recording or reboot performed |

The screenshot is in ignored `private-evidence/installed-overview.png` and is not
published. uiautomator dump to /dev/tty returned only its completion message, so
no UI-tree evidence is claimed. Installation/rendering does not establish that
the unfinished input backends or stock camera capture work.
