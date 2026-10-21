package dev.opluskeycontrol.input

import java.io.*
import org.junit.Assert.*
import org.junit.Test

class PolicyWireTest {
    @Test fun utf8UsesBytesNotCharacters() {
        val buffer = ByteArrayOutputStream()
        val original = "Tiếng Việt · Plus 🔦"
        PolicyWire.writeText(DataOutputStream(buffer), original)
        assertEquals(original, PolicyWire.readText(DataInputStream(ByteArrayInputStream(buffer.toByteArray()))))
    }
    @Test fun rejectsNegativeLengthBeforeAllocation() { invalid(-1) }
    @Test fun rejectsExcessiveLengthBeforeAllocation() { invalid(PolicyWire.MAX_BYTES + 1) }
    @Test fun rejectsZeroLengthConfiguration() { invalid(0) }
    private fun invalid(size: Int) {
        val buffer = ByteArrayOutputStream(); DataOutputStream(buffer).writeInt(size)
        try { PolicyWire.readText(DataInputStream(ByteArrayInputStream(buffer.toByteArray()))); fail("Accepted invalid length") }
        catch (_: IllegalArgumentException) { }
    }
    @Test(expected = EOFException::class) fun truncatedPayloadFailsClosed() {
        val buffer = ByteArrayOutputStream(); DataOutputStream(buffer).apply { writeInt(100); writeByte(1) }
        PolicyWire.readText(DataInputStream(ByteArrayInputStream(buffer.toByteArray())))
    }
}
