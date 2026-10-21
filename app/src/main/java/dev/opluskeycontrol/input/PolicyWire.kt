package dev.opluskeycontrol.input

import java.io.DataInputStream
import java.io.DataOutputStream

/** Bounded protocol, no shell commands/intents/Parcelable classes on the wire. */
object PolicyWire {
    const val NAME = "dev.opluskeycontrol.policy.v1"
    const val READY = "dev.opluskeycontrol.BACKEND_READY"
    const val PERMISSION = "dev.opluskeycontrol.permission.BACKEND"
    const val MAGIC = 0x4f504b31
    const val CONFIG = 1
    const val ACTION = 2
    const val MAX_BYTES = 1_000_000
    // Stay well below Binder's shared transaction-buffer limit.
    const val MAX_BINDER_BYTES = 262_144
    fun binderText(text: String): ByteArray = text.toByteArray(Charsets.UTF_8).also {
        require(it.size in 1..MAX_BINDER_BYTES)
    }
    fun binderText(bytes: ByteArray): String {
        require(bytes.size in 1..MAX_BINDER_BYTES)
        return bytes.toString(Charsets.UTF_8)
    }
    fun writeText(out: DataOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_BYTES)
        out.writeInt(bytes.size); out.write(bytes)
    }
    fun readText(input: DataInputStream): String {
        val size = input.readInt(); require(size in 1..MAX_BYTES)
        return ByteArray(size).also { input.readFully(it) }.toString(Charsets.UTF_8)
    }
}
