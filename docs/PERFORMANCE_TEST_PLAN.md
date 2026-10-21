# Performance protocol (no results yet)

Current status: NOT_TESTED. No CPU%, wakeup count, RAM size or battery-drain
improvement is claimed. Absence of a polling loop is not a battery benchmark.

After approval for each backend test, record screen/charging/network/thermal
conditions. Compare disabled vs enabled with the same firmware and rule set.
Keep test duration and physical button sequence matched. Exclude Android Studio,
ADB/logcat streaming and camera/recording activity from idle measurements.

Read-only observations: scoped dumpsys meminfo/cpuinfo, package-associated jobs/
alarms/wakelocks and suitable Perfetto/Simpleperf captures when separately
approved. Batterystats reset or test setting changes are writes and need scope.
Report raw duration and actual readings with noise/charging limitations. Deep
sleep support requires demonstrating driver event availability; sysfs wakeup
enabled alone is insufficient. No permanent wakelock may be used to pass a test.

Measure gesture latency from physical event to decision/action acknowledgement,
separately from a request being accepted. Measure unmatched volume behavior and
the OEM single/double/long paths before considering suppression. Force-stop,
backend loss, uninstall and reboot checks require their own approved procedure.
