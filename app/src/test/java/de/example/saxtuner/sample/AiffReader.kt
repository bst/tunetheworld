package de.example.saxtuner.sample

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal AIFF reader for tests: 24-bit big-endian PCM, any channel count, mixed down to mono. */
object AiffReader {
    class Audio(val sampleRate: Int, val samples: FloatArray)

    fun read(file: File): Audio {
        val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.BIG_ENDIAN)
        require(tag(buf, 0) == "FORM" && tag(buf, 8) == "AIFF") { "${file.name}: not an AIFF file" }

        var channels = 0
        var frames = 0
        var bits = 0
        var rate = 0.0
        var dataStart = -1
        var pos = 12
        while (pos + 8 <= buf.limit()) {
            val id = tag(buf, pos)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            when (id) {
                "COMM" -> {
                    channels = buf.getShort(body).toInt()
                    frames = buf.getInt(body + 2)
                    bits = buf.getShort(body + 6).toInt()
                    rate = extended(buf, body + 8)
                }
                "SSND" -> dataStart = body + 8 + buf.getInt(body) // skip offset and block size fields
            }
            pos = body + size + (size and 1) // chunks are padded to even length
        }
        require(channels > 0 && dataStart >= 0) { "${file.name}: missing COMM or SSND chunk" }
        require(bits == 24) { "${file.name}: expected 24-bit PCM but found $bits-bit" }
        require(rate == 44100.0) { "${file.name}: expected 44100 Hz but found $rate Hz" }

        val out = FloatArray(frames)
        var p = dataStart
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                // Sign-extend the 3 big-endian bytes
                val v = (buf.get(p).toInt() shl 16) or
                    ((buf.get(p + 1).toInt() and 0xFF) shl 8) or
                    (buf.get(p + 2).toInt() and 0xFF)
                sum += v / 8388608f
                p += 3
            }
            out[i] = sum / channels
        }
        return Audio(rate.toInt(), out)
    }

    private fun tag(buf: ByteBuffer, at: Int) = String(ByteArray(4) { buf.get(at + it) }, Charsets.US_ASCII)

    /** 80-bit IEEE extended precision: 1 sign bit, 15 exponent bits, 64 mantissa bits. */
    private fun extended(buf: ByteBuffer, at: Int): Double {
        val exp = buf.getShort(at).toInt() and 0x7FFF
        val mantissa = buf.getLong(at + 2)
        val unsigned = (mantissa ushr 1).toDouble() * 2.0 // avoid signed overflow of the top bit
        return unsigned * Math.pow(2.0, (exp - 16383 - 63).toDouble())
    }
}
