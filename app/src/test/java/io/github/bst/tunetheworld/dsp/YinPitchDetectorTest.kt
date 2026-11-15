package io.github.bst.tunetheworld.dsp

import io.github.bst.tunetheworld.engine.TunerConfig
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

    // Periods of 16.7 and 10.5 samples: here the interpolation on raw d matters most (on d′ it read 2–5 cents sharp)
    @Test fun pureSineViolinE7() = assertPitch(2637.02, floatArrayOf(0.5f), 1.0)

    @Test fun pureSinePiccoloC8() = assertPitch(4186.01, floatArrayOf(0.5f), 1.0)

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
    fun dominantThirdHarmonicIsNotATwelfthHigh() {
        // Like the oboe's B♭4 in the test recordings: the 3rd harmonic is about 20 dB above the fundamental
        val r = detector().detect(tone(466.16, floatArrayOf(0.1f, 0.15f, 1.0f, 0.1f)))
        assertNotNull(r)
        assertTrue("got ${r!!.frequencyHz} Hz", abs(cents(r.frequencyHz, 466.16)) < 2.0)
    }

    @Test
    fun fadingFundamentalKeepsTheOctaveOfThePreviousBlock() {
        // A low string dying away: in the second block the fundamental is almost gone and only even harmonics are left,
        // so the half period is a near-perfect match that the octave check cannot reject
        val strong = tone(65.41, floatArrayOf(1.0f, 1.0f, 0.5f, 0.5f))
        val faded = tone(65.41, floatArrayOf(0.01f, 1.0f, 0.0f, 0.5f))
        val alone = detector().detect(faded)
        assertNotNull(alone)
        assertTrue("without memory: ${alone!!.frequencyHz} Hz", abs(cents(alone.frequencyHz, 130.81)) < 5.0)

        val d = detector()
        d.detect(strong)
        val r = d.detect(faded)
        assertNotNull(r)
        assertTrue("with memory: ${r!!.frequencyHz} Hz", abs(cents(r.frequencyHz, 65.41)) < 2.0)

        d.reset()
        val afterReset = d.detect(faded)
        assertTrue("after reset: ${afterReset!!.frequencyHz} Hz", abs(cents(afterReset.frequencyHz, 130.81)) < 5.0)
    }

    @Test
    fun noteChangeAnOctaveUpIsNotHeldBack() {
        // The memory must not keep the old period: the new note also repeats after it, but no better than at its own
        val d = detector()
        d.detect(tone(65.41, floatArrayOf(1.0f, 0.5f, 0.3f)))
        val r = d.detect(tone(130.81, floatArrayOf(1.0f, 0.5f, 0.3f)))
        assertNotNull(r)
        assertTrue("got ${r!!.frequencyHz} Hz", abs(cents(r.frequencyHz, 130.81)) < 2.0)
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
