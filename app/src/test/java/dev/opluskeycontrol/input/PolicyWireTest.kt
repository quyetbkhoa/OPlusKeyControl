package dev.opluskeycontrol.input

import java.io.*
import org.junit.Assert.*
import org.junit.Test

class PolicyWireTest {
    @Test fun binderUtf8RoundTripAndBoundary() {
        val text = "Tiếng Việt 🔦"
        assertEquals(text, PolicyWire.binderText(PolicyWire.binderText(text)))
        assertEquals(PolicyWire.MAX_BINDER_BYTES, PolicyWire.binderText("a".repeat(PolicyWire.MAX_BINDER_BYTES)).size)
    }
    @Test(expected = IllegalArgumentException::class) fun binderRejectsOversizedUtf8ByBytes() {
        PolicyWire.binderText("ệ".repeat(PolicyWire.MAX_BINDER_BYTES / 3 + 1))
    }
    @Test(expected = IllegalArgumentException::class) fun binderRejectsEmptyIncomingConfiguration() {
        PolicyWire.binderText(byteArrayOf())
    }
    @Test(expected = IllegalArgumentException::class) fun binderRejectsOversizedIncomingConfiguration() {
        PolicyWire.binderText(ByteArray(PolicyWire.MAX_BINDER_BYTES + 1))
    }
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
