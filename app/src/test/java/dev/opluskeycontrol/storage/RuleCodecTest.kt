package dev.opluskeycontrol.storage

import dev.opluskeycontrol.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZonedDateTime
import org.json.JSONObject
import org.json.JSONArray

class RuleCodecTest {
    private fun configuration() = Configuration(listOf(Rule("night", Trigger(Button.PLUS, Gesture.DOUBLE), Action(ActionKind.APP, "dev.test"), Schedule(1320, 360, setOf(DayOfWeek.MONDAY)), enabled = false)), timing = Timing(350, 700, 150, 40))
    @Test fun roundTripRemovesScheduleAndPreservesTimingAndTarget() {
        val c = configuration()
        assertEquals(c.copy(rules = c.rules.map { it.copy(schedule = null) }), RuleCodec.decode(RuleCodec.encode(c)))
        assertFalse(JSONObject(RuleCodec.encode(c)).getJSONArray("rules").getJSONObject(0).has("schedule"))
    }
    @Test fun legacyWindowBecomesAlwaysAvailable() {
        val c = configuration().copy(rules = configuration().rules.map { it.copy(enabled = true) })
        val json = JSONObject(RuleCodec.encode(c))
        json.getJSONArray("rules").getJSONObject(0).put("schedule", window(1320, 360))
        val now = ZonedDateTime.parse("2026-10-19T12:00:00+07:00")
        val migrated = RuleCodec.decode(json.toString(), now)
        val rule = migrated.rules.single()
        assertNull(rule.schedule)
        assertTrue(rule.enabled)
        assertEquals(Resolution.Match(rule), RuleResolver.resolve(migrated.rules, rule.trigger, now))
    }
    @Test fun legacyDisjointWindowsKeepCurrentWinnerAndAllTargets() {
        val json = JSONObject(RuleCodec.encode(Configuration(listOf(configuration().rules.single().copy(schedule = null, enabled = true)))))
        val list = json.getJSONArray("rules")
        list.getJSONObject(0).put("schedule", window(480, 720))
        list.put(JSONObject(list.getJSONObject(0).toString()).put("id", "afternoon").put("package", "dev.afternoon").put("schedule", window(720, 1080)))
        val migrated = RuleCodec.decode(json.toString(), ZonedDateTime.parse("2026-10-19T13:00:00+07:00"))
        assertEquals(2, migrated.rules.size)
        assertTrue(migrated.rules.all { it.schedule == null })
        assertEquals("afternoon", migrated.rules.single { it.enabled }.id)
        assertEquals("dev.test", migrated.rules.first().action.packageName)
        assertFalse(migrated.rules.any { RuleResolver.conflicts(it, migrated.rules) })
        assertEquals(migrated, RuleCodec.decode(RuleCodec.encode(migrated)))
    }
    @Test fun alwaysOnMappingsKeepAllExistingFields() {
        val c = configuration().copy(enabled = true, tier = Tier.ROOT, rules = listOf(configuration().rules.single().copy(schedule = null, priority = 42)))
        assertEquals(c, RuleCodec.decode(RuleCodec.encode(c)))
    }
    private fun window(start: Int, end: Int) = JSONObject().put("start", start).put("end", end).put("days", JSONArray(listOf(1)))
    @Test fun emptyConfigurationRoundTrip() { assertEquals(Configuration(), RuleCodec.decode(RuleCodec.encode(Configuration()))) }
    @Test fun oldConfigurationEnablesHapticsByDefaultWithoutChangingMappings() {
        val c = configuration().copy(rules = configuration().rules.map { it.copy(schedule = null) })
        val old = JSONObject(RuleCodec.encode(c)).apply { remove("hapticFeedback") }
        assertEquals(c, RuleCodec.decode(old.toString()))
        assertTrue(RuleCodec.decode(old.toString()).hapticFeedback)
    }
    @Test fun vibrationPreferenceSurvivesSaveAndRestoreWithRuleStates() {
        for(enabled in listOf(true, false)) {
            val c = configuration().copy(hapticFeedback = enabled, rules = configuration().rules.map { it.copy(schedule = null) })
            assertEquals(c, RuleCodec.decode(RuleCodec.encode(c)))
        }
    }
    @Test(expected = IllegalArgumentException::class) fun futureVersionRejected() { RuleCodec.decode(RuleCodec.encode(configuration()).replace("\"version\": 1", "\"version\": 99")) }
    @Test(expected = IllegalArgumentException::class) fun duplicateIdsRejected() { val c = configuration(); RuleCodec.decode(RuleCodec.encode(c.copy(rules = c.rules + c.rules))) }
    @Test(expected = IllegalArgumentException::class) fun oversizeInputRejected() { RuleCodec.decode(" ".repeat(1_000_001)) }
    @Test(expected = IllegalArgumentException::class) fun importedPowerSingleRejected() { RuleCodec.decode(RuleCodec.encode(configuration()).replace("PLUS", "POWER").replace("DOUBLE", "SINGLE")) }
    @Test(expected = IllegalArgumentException::class) fun enabledAmbiguityRejected() {
        val rule = configuration().rules.first().copy(enabled = true)
        RuleCodec.decode(RuleCodec.encode(Configuration(listOf(rule, rule.copy(id = "other")))))
    }
}
