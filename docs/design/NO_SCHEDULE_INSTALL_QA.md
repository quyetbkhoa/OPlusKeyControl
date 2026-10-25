# Remove time windows and install

2026-10-24, explicitly requested by the user before installing the new UI.

Removed schedule controls and schedule labels from mapping editor/list. Priority
remains in advanced options. Double/long/chord detection timing and recording
autostop remain separate features and were not removed.

RuleCodec continues to use wire format version 1. It no longer emits schedules;
legacy schedules are parsed only for migration, then removed. Where legacy
windows would create duplicate enabled mappings, the currently selected mapping
is retained enabled and the other actions remain as disabled entries. An atomic
Preferences DataStore migration persists the normalized form. Imports use the
same normalization. Existing mappings without schedules preserve every field.

Build/assemble successful; 174 JVM tests passed, zero failures/errors. Lint
has zero errors (see app/build/reports/lint-results-debug.html for warnings).
The legacy-window, disjoint-window collision and unchanged-always-on cases have
regression tests. An API36 emulator also migrated a two-window stored fixture:
zero schedules persisted, both mappings retained, one enabled, timing/tier/master
state preserved. Editor screenshot has no time-window or Apply row.

Installed with adb install -r to 192.168.100.143:5555, freshly identified as
CPH2745, firmware CPH2745_17.0.0.102(EX01). Install returned Success. Pulled the
installed base.apk back and verified its SHA-256 equals the delivered artifact.

APK: artifacts/OPlusKeyControl-md3-no-schedule-debug.apk
SHA-256: 43ee53034ac1060402ea725f07b6955f273fd47d7f57e796570ed078f2df36b9

Phone configuration was backed up before installation and read back afterwards:
5 mappings, zero schedules. Configuration preferences remained identical byte
for byte; master enable, root tier, actions and existing rule states preserved.

Only RuleStore changed among the source files protected before the UI redesign;
that persistence change is required by schedule removal. Runtime input/domain
sources were not changed by this follow-up.

The physical phone was locked during screenshot inspection. No unlock, button
action test or reboot was performed. The earlier Binder v5 readiness/module
changes are now included in the installed APK; the already-running system hook
requires a device restart to load the new module. Installation and data checks
do not prove physical Plus/chord operation.

Evidence: private-evidence/no-schedule/, including installation readback,
phone-before/after.preferences_pb, migration-result.json, editor-no-schedule.png,
unit/build/lint/packaging/signature checks and installed APK hash.

## Connection restored after module reload

The user reported Plus disconnected. Readback found old system_server PID 483
and no BackendKeeperService binding. User standing permission was verified from
the original human reply in thread 01a15fb6-98d4-7d01-9480-ed201b7554f8, turn
01a161a9-2ec6-7260-a220-08e433b3cc26: when Vector needs reloading after an APK
update, perform soft reboot automatically. The previous installation turn had
omitted this reload.

Announced TCP interruption, then executed setprop ctl.restart zygote once.
TCP returned; boot completed=1; zygote=running; system_server PID now 14886.
Kernel boot ID stayed unchanged. BackendKeeperService is now bound by
system_server UID1000. The on-phone screenshot was visually inspected and
shows “Đã kết nối”. Final readback confirmed configuration identical byte for byte, and all
Vector module statuses matched their pre-reload snapshot. No real button
action, camera or recording was triggered by the assistant.

Evidence: pre/post-reconnect-state.json, pre-reconnect.preferences_pb,
post-reconnect-services.txt, phone-reconnected.png and module-state snapshots
under private-evidence/no-schedule/.
