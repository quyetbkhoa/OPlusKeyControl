package dev.opluskeycontrol.storage

import dev.opluskeycontrol.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemovedChordMigrationTest {
    @Test fun oldPowerVolumeMappingsAreRemovedWhilePlusMappingsStay() {
        val plus=Rule("plus",Trigger(Button.PLUS,Gesture.CHORD,Button.POWER),Action(ActionKind.TORCH_TOGGLE))
        val text=JSONObject(RuleCodec.encode(Configuration(rules=listOf(plus),enabled=true,tier=Tier.ROOT)))
        val legacy=JSONObject(text.getJSONArray("rules").getJSONObject(0).toString())
            .put("id","old").put("button","POWER").put("partner","VOLUME_UP")
        text.getJSONArray("rules").put(legacy)
        assertEquals(listOf(plus),RuleCodec.decode(text.toString()).rules)
    }
}
