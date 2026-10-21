# Architecture and invariants

A separate finite observation APK proved Plus reaches IMS; see POLICY_PROBE.md.
An opt-in runtime implementation now connects policy ownership, authenticated
transport, rules and actions; see RUNTIME_BACKEND.md for complete-coverage limits,
lifecycle design and device-test gaps. Normal build callbacks compile to no-op.
No runtime remapping build has been activated. The original design notes below
predate that candidate and describe the normal observer-only build.

`InputSource -> ownership -> ButtonEvent(monotonic) -> GestureEngine -> RuleResolver(zoned clock) -> ActionExecutor`

`domain/` is JVM-only. It does not authorize suppression. An observation source
cannot turn recognized candidates into input interception. The shipped Basic
observer is deliberately not connected to action dispatch. Shizuku detection and
the self-scope Vector probe are separate integrations, not capture backends.

Gesture priority: an uncommitted chord consumes both components; a committed long
cannot later also chord. Long cancels pending click; double consumes pending
single. Held modifier means hold trigger.button, then partner; reverse order is
allowed only within chordMs. One-shot deadlines support idle without polling.
Intervals use monotonic timestamps. Disconnect/cancel/regression resets state;
foreign source/device is ignored until explicit reset. No native source or policy
suppression exists yet. Power long is never treated as the first click of double.

Schedule: `[start,end)` by local minute, selected day is the starting day for
overnight windows. Equal bounds invalid; use no schedule for default. Specific
beats default, then higher numeric priority wins. Equal-priority overlap fails
open as Conflict and is rejected on validated storage writes/import. Disabled
drafts may overlap but cannot both be enabled. Current clock/zone is supplied
per resolution; no repeating schedule timer or permanent wakelock.

Storage is a single atomic DataStore preference with JSON schema version 1.
Rules, bounds, days, reserved triggers, IDs, component syntax, count and byte-size
limits are validated. Future schemas are rejected; failed loads default disabled.
Restore is validated before atomic replacement and always disables. No free-form
intent, shell or extras. Manual DI keeps Android IO outside the testable domain.

Actions are serialized on the application main coroutine scope with a mutex;
CameraManager torch callbacks drive observed torch state. Start requests are not
reported as completed captures/launches. Recorder uses microphone FGS only while
recording and a one-shot auto-stop deadline, never background polling. No action
claims target UID/signature. Internal/protected activities remain unselectable.

App discovery scans labels on Dispatchers.IO; activity details are loaded for one
chosen package, icons only for visible rows, package broadcasts invalidate the
picker. The current implementation enumerates visible application labels as a
list; full multi-user discovery and disk cache/paging are not yet implemented.
No package list persists or leaves the device. QUERY_ALL_PACKAGES is explicitly
justified for the GitHub sideload utility, not a Play distribution declaration.
