package dev.opluskeycontrol.inspector
import org.junit.Assert.*
import org.junit.Test
class InspectorTextTest {
    @Test fun passwordNeverExportsTextOrDescription() { assertEquals("[password]",InspectorText.field("secret",true)); assertEquals("[password]",InspectorText.field("secret description",true)) }
    @Test fun textPreservesVietnameseAndEscapesStructuralNewlines() { assertEquals("Cài đặt\\nMàn hình",InspectorText.field("Cài đặt\nMàn hình",false)); assertEquals(600,InspectorText.field("x".repeat(1000),false).length) }
    @Test fun activityDoesNotMistakeHistoricalTasksOrOtherPackagesForForeground() {
        val tasks = "ActivityRecord{old com.target/.Old}\nmResumedActivity: ActivityRecord{u0 com.other/.Main t1}\ntopResumedActivity=ActivityRecord{u0 com.target/.Current t2}"
        assertEquals("com.target/.Current",InspectorText.activity(tasks,"com.target")); assertNull(InspectorText.activity(tasks,"com.absent"))
    }
}
