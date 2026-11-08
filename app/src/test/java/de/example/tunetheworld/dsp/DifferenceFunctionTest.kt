package de.example.tunetheworld.dsp

import de.example.tunetheworld.engine.TunerConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/** The FFT version must match the plain definition, computed here in double precision as the reference. */
class DifferenceFunctionTest {
    private val w = TunerConfig.WINDOW_SIZE / 2
    private val maxLag = 1471 // the detector's largest lag at 30 Hz, plus one
    private val fft = DifferenceFunction(w, maxLag)

    private fun reference(x: FloatArray): DoubleArray = DoubleArray(maxLag + 1) { tau ->
        var sum = 0.0
        for (j in 0 until w) {
            val d = x[j].toDouble() - x[j + tau].toDouble()
            sum += d * d
        }
        sum
    }

    /** Largest error relative to the window energy, the scale at which FFT rounding happens. */
    private fun maxError(x: FloatArray): Double {
        val out = FloatArray(maxLag + 1)
        fft.compute(x, out)
        val ref = reference(x)
        var energy = 0.0
        for (j in 0 until w) energy += x[j].toDouble() * x[j]
        var worst = 0.0
        for (tau in 0..maxLag) {
            // The float result can be no closer than its own rounding of the reference value
            val err = abs(out[tau] - ref[tau]) - ref[tau] * 1e-7
            worst = maxOf(worst, err / maxOf(energy, 1e-30))
        }
        return worst
    }

    private fun signal(gen: (Int) -> Double) = FloatArray(TunerConfig.WINDOW_SIZE) { gen(it).toFloat() }

    @Test
    fun matchesDefinitionOnNoise() {
        val rnd = Random(1)
        val e = maxError(signal { rnd.nextDouble() - 0.5 })
        assertTrue("error $e", e < 1e-12)
    }

    @Test
    fun matchesDefinitionOnHarmonicTones() {
        for (f0 in listOf(32.7, 65.41, 440.0, 2637.0)) {
            val e = maxError(signal { i -> (1..5).sumOf { h -> sin(2 * PI * f0 * h * i / 44100) / h } })
            assertTrue("$f0 Hz: error $e", e < 1e-12)
        }
    }

    @Test
    fun deepDipsStayAccurate() {
        // Perfectly periodic: d is exactly 0 at the period (100 samples) and must not come out noticeably above it
        val out = FloatArray(maxLag + 1)
        fft.compute(signal { i -> sin(2 * PI * i / 100.0) + 0.3 * sin(6 * PI * i / 100.0) }, out)
        val energy = (0 until w).sumOf { 0.5 + 0.045 }
        assertTrue("d(100) = ${out[100]}", out[100] / energy < 1e-9)
        assertTrue("d(200) = ${out[200]}", out[200] / energy < 1e-9)
    }

    @Test
    fun silenceGivesZero() {
        val out = FloatArray(maxLag + 1) { 1f }
        fft.compute(FloatArray(TunerConfig.WINDOW_SIZE), out)
        assertTrue(out.all { it == 0f })
    }
}
