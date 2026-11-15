package io.github.bst.tunetheworld.dsp

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalUtilsTest {
    @Test
    fun rmsOfSineIsAmplitudeOverRootTwo() {
        val buf = FloatArray(44100) { sin(2.0 * PI * 441.0 * it / 44100).toFloat() * 0.5f }
        assertEquals(0.5f / Math.sqrt(2.0).toFloat(), rms(buf, 0, buf.size), 0.001f)
    }

    @Test
    fun rmsOfSilenceIsZeroAndRespectsRange() {
        val buf = FloatArray(10) { if (it < 5) 1f else 0f }
        assertEquals(0f, rms(buf, 5, 5), 0f)
        assertEquals(1f, rms(buf, 0, 5), 0.0001f)
    }

    @Test
    fun dbConversionRoundTrips() {
        assertEquals(-20f, amplitudeToDb(0.1f, -70f), 0.001f)
        assertEquals(0.1f, dbToAmplitude(-20f), 0.0001f)
        assertEquals(-70f, amplitudeToDb(0f, -70f), 0f)
    }
}
