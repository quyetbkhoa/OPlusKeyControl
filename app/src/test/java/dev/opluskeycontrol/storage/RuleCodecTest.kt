package dev.opluskeycontrol.storage

import dev.opluskeycontrol.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek

class RuleCodecTest {
    private fun configuration() = Configuration(listOf(Rule("night", Trigger(Button.PLUS, Gesture.DOUBLE), Action(ActionKind.APP, "dev.test"), Schedule(1320, 360, setOf(DayOfWeek.MONDAY)), enabled = false)), timing = Timing(350, 700, 150, 40))
    @Test fun roundTripPreservesScheduleTimingAndTarget() { val c = configuration(); assertEquals(c, RuleCodec.decode(RuleCodec.encode(c))) }
    @Test fun emptyConfigurationRoundTrip() { assertEquals(Configuration(), RuleCodec.decode(RuleCodec.encode(Configuration()))) }
    @Test(expected = IllegalArgumentException::class) fun futureVersionRejected() { RuleCodec.decode(RuleCodec.encode(configuration()).replace("\"version\": 1", "\"version\": 99")) }
    @Test(expected = IllegalArgumentException::class) fun duplicateIdsRejected() { val c = configuration(); RuleCodec.decode(RuleCodec.encode(c.copy(rules = c.rules + c.rules))) }
    @Test(expected = IllegalArgumentException::class) fun oversizeInputRejected() { RuleCodec.decode(" ".repeat(1_000_001)) }
    @Test(expected = IllegalArgumentException::class) fun importedPowerSingleRejected() { RuleCodec.decode(RuleCodec.encode(configuration()).replace("PLUS", "POWER").replace("DOUBLE", "SINGLE")) }
    @Test(expected = IllegalArgumentException::class) fun enabledAmbiguityRejected() {
        val rule = configuration().rules.first().copy(enabled = true)
        RuleCodec.decode(RuleCodec.encode(Configuration(listOf(rule, rule.copy(id = "other")))))
    }
}
