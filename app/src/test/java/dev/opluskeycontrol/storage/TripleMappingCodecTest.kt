package dev.opluskeycontrol.storage

import dev.opluskeycontrol.domain.*
import org.junit.Assert.*
import org.junit.Test

class TripleMappingCodecTest {
    @Test fun plusAndPowerTripleMappingsSurviveBackupRoundTrip() {
        val rules = listOf(Button.PLUS,Button.POWER).map { button ->
            Rule(button.name,Trigger(button,Gesture.TRIPLE),Action(ActionKind.TORCH_TOGGLE))
        }
        val original = Configuration(rules=rules,enabled=true,tier=Tier.ROOT)
        assertEquals(original,RuleCodec.decode(RuleCodec.encode(original)))
    }
}
