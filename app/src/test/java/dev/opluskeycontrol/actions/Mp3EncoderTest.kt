package dev.opluskeycontrol.actions

import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class Mp3EncoderTest {
    @Test fun streamsRealMp3FromUnevenPcmBlocks() {
        // Synthetic signal only: no microphone or user recording is involved.
        val target = File("build/test-fixtures/tone.mp3").apply { requireNotNull(parentFile).mkdirs() }
        val encoded = ByteArray(16384)
        target.outputStream().use { output ->
            Mp3Encoder().use { encoder ->
                var position = 0
                while (position < 44100) {
                    val count = minOf(1009, 44100 - position)
                    val pcm = ShortArray(count) { (sin(2 * PI * 440 * (position + it) / 44100) * 12000).toInt().toShort() }
                    output.write(encoded, 0, encoder.encode(pcm, count, encoded))
                    position += count
                }
                output.write(encoded, 0, encoder.finish(encoded))
            }
        }
        val bytes = target.readBytes()
        assertTrue(bytes.size > 10000)
        assertEquals(0xff, bytes[0].toInt() and 0xff)
        // MPEG-1 Layer III header, rather than an AAC/MP4 file renamed to .mp3.
        assertEquals(0xfa, bytes[1].toInt() and 0xfe)
    }
    @Test fun rejectsEncodingAfterClose() {
        val encoder = Mp3Encoder()
        encoder.close(); encoder.close()
        assertThrows(IllegalStateException::class.java) { encoder.encode(shortArrayOf(0), 1, ByteArray(16384)) }
    }
}
