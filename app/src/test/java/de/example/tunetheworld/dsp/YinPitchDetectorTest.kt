package de.example.tunetheworld.dsp

import de.example.tunetheworld.engine.TunerConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YinPitchDetectorTest {
    private val sampleRate = 44100
    private fun detector() = YinPitchDetector(
        sampleRate,
        TunerConfig.WINDOW_SIZE,
        TunerConfig.MIN_FREQ_HZ,
        TunerConfig.MAX_FREQ_HZ,
        TunerConfig.YIN_THRESHOLD
    )

    /** Sum of sines at f0, 2*f0, 3*f0 ... with the given amplitudes. */
    private fun tone(f0: Double, harmonics: FloatArray, n: Int = 4096): FloatArray = FloatArray(n) { i ->
        var s = 0.0
        for (h in harmonics.indices) {
            s += harmonics[h] * sin(2.0 * PI * f0 * (h + 1) * i / sampleRate)
        }
        s.toFloat()
    }

    private fun cents(measured: Float, expected: Double) = 1200.0 * log2(measured / expected)

    private fun assertPitch(f0: Double, harmonics: FloatArray, maxCents: Double) {
        val r = detector().detect(tone(f0, harmonics))
        assertNotNull("no pitch for $f0 Hz", r)
        val err = cents(r!!.frequencyHz, f0)
        assertTrue("$f0 Hz: measured ${r.frequencyHz} Hz, off by $err cents", abs(err) < maxCents)
    }

    @Test fun pureSineMiddle() = assertPitch(440.0, floatArrayOf(0.5f), 1.0)

    @Test fun pureSineLow() = assertPitch(103.83, floatArrayOf(0.5f), 1.0)

    @Test fun pureSineHigh() = assertPitch(1000.0, floatArrayOf(0.5f), 1.0)

    @Test fun pureSineDoubleBassLowE() = assertPitch(41.20, floatArrayOf(0.5f), 1.0)

    @Test fun pureSineFiveStringBassLowB() = assertPitch(30.87, floatArrayOf(0.5f), 1.0)

    // Period of 16.7 samples: the lag falls between whole samples, so the accuracy is lower up here
    @Test fun pureSineViolinE7() = assertPitch(2637.02, floatArrayOf(0.5f), 3.0)

    @Test fun pureSinePiccoloC8() = assertPitch(4186.01, floatArrayOf(0.5f), 5.0)

    @Test
    fun lowStringWithWeakFundamentalIsNotAnOctaveHigh() {
        // Fundamental almost gone, as on a low string dying away
        val r = detector().detect(tone(65.41, floatArrayOf(0.1f, 1.0f, 0.5f, 0.3f)))
        assertNotNull(r)
        assertTrue("got ${r!!.frequencyHz} Hz", abs(cents(r.frequencyHz, 65.41)) < 2.0)
    }

    @Test
    fun saxLikeLowToneIsNotAnOctaveHigh() {
        // Second partial louder than the fundamental, as on a real low sax note
        val r = detector().detect(tone(116.54, floatArrayOf(0.4f, 1.0f, 0.7f, 0.5f, 0.3f)))
        assertNotNull(r)
        assertTrue("got ${r!!.frequencyHz} Hz", abs(cents(r.frequencyHz, 116.54)) < 2.0)
    }

    @Test
    fun sineWithNoise() {
        val rnd = Random(42)
        val buf = tone(440.0, floatArrayOf(0.5f))
        for (i in buf.indices) buf[i] += (rnd.nextFloat() * 2f - 1f) * 0.1f
        val r = detector().detect(buf)
        assertNotNull(r)
        assertTrue("got ${r!!.frequencyHz} Hz", abs(cents(r.frequencyHz, 440.0)) < 3.0)
    }

    @Test
    fun silenceGivesNull() {
        assertNull(detector().detect(FloatArray(4096)))
    }

    @Test
    fun noiseIsRejectedOrLowConfidence() {
        val rnd = Random(42)
        val buf = FloatArray(4096) { rnd.nextFloat() - 0.5f }
        val r = detector().detect(buf)
        assertTrue(r == null || r.confidence < 0.8f)
    }

    @Test
    fun runtime() {
        val d = detector()
        val buf = tone(440.0, floatArrayOf(0.5f))
        repeat(500) { d.detect(buf) } // warm-up, so the JIT has compiled the hot loops
        val start = System.nanoTime()
        repeat(1000) { d.detect(buf) }
        val seconds = (System.nanoTime() - start) / 1e9
        println("1000 detect() calls: $seconds s")
        // About 0.35 s on a desktop JVM with the FFT difference function (2.8 s with the plain loop); room for slow CI
        assertTrue("took $seconds s", seconds < 1.5)
    }
}
