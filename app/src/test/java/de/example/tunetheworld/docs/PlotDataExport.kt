package de.example.tunetheworld.docs

import de.example.tunetheworld.engine.TunerConfig
import de.example.tunetheworld.sample.AiffReader
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Writes the data behind the figures in PITCH_DETECTION.md. Skipped unless the environment variable PLOT_DATA_DIR is
 * set; docs/plots/README.md explains how to regenerate the figures.
 */
class PlotDataExport {
    private val sampleRate = TunerConfig.SAMPLE_RATE
    private val size = TunerConfig.WINDOW_SIZE

    @Test
    fun export() {
        val path = System.getenv("PLOT_DATA_DIR")
        assumeTrue("PLOT_DATA_DIR is not set", path != null)
        val out = File(path!!).apply { mkdirs() }

        // Steps 1 and 2: the sax-like test tone from YinPitchDetectorTest
        val sax = tone(116.54, floatArrayOf(0.4f, 1.0f, 0.7f, 0.5f, 0.3f))
        File(out, "functions-signal.csv").writeText(sax.joinToString("\n", postfix = "\n"))
        write(out, "functions", YinTrace(sax), 116.54)

        // Step 3: plain YIN would take the shallow dip at half the period
        val clarinet = recording("BbClarinet.ff.Bb6.stereo.aif", block = 88)
        check(clarinet.plainLag != clarinet.nearDeepestLag) { "clarinet block no longer shows the near-deepest rule" }
        write(out, "clarinet", clarinet, midiHz(94))

        // Step 3b: the second harmonic dominates and the dip at twice the lag is much deeper
        val altoSax = recording("AltoSax.NoVib.ff.D3.stereo.aif", block = 15)
        check(altoSax.octaveApplied) { "alto sax block no longer shows the octave check" }
        write(out, "altosax", altoSax, midiHz(50))

        // Step 4: a high sine whose period (16.72 samples) falls between whole samples
        write(out, "interpolation", YinTrace(tone(2637.02, floatArrayOf(0.5f))), 2637.02)

        // Step 4: d(τ) between whole samples, exact for a pure sine, around the dip of a middle and a high note
        write(out, "sine440", YinTrace(tone(440.0, floatArrayOf(0.5f))), 440.0)
        continuous(out, "sine440", 440.0)
        continuous(out, "interpolation", 2637.02)

        // Step 4: error with and without interpolation for pure sines across the whole range
        val sweep = StringBuilder("f,detected,integer_lag,raw_d_parabola\n")
        val steps = 400
        for (i in 0..steps) {
            val f = exp(ln(TunerConfig.MIN_FREQ_HZ.toDouble()) + i * ln(4186.01 / TunerConfig.MIN_FREQ_HZ) / steps)
            val t = YinTrace(tone(f, floatArrayOf(0.5f)))
            t.checkAgainstDetector()
            // The same parabola fitted to the raw d(τ) instead of d′(τ), for comparison
            val s0 = t.diff[t.finalLag - 1].toDouble()
            val s1 = t.diff[t.finalLag].toDouble()
            val s2 = t.diff[t.finalLag + 1].toDouble()
            val raw = t.finalLag + 0.5 * (s0 - s2) / (s0 - 2 * s1 + s2)
            sweep.append("$f,${t.frequencyHz},${sampleRate.toDouble() / t.finalLag},${sampleRate / raw}\n")
        }
        File(out, "sweep.csv").writeText(sweep.toString())
    }

    private fun write(out: File, name: String, t: YinTrace, expectedHz: Double) {
        t.checkAgainstDetector()
        val csv = StringBuilder("tau,d,cmnd\n")
        for (tau in 0..t.tauMax) csv.append("$tau,${t.diff[tau]},${t.cmnd[tau]}\n")
        File(out, "$name.csv").writeText(csv.toString())
        val meta = mapOf(
            "expected_hz" to expectedHz,
            "threshold" to t.threshold,
            "deepest" to t.deepest,
            "accept" to t.accept,
            "plain_lag" to t.plainLag,
            "near_deepest_lag" to t.nearDeepestLag,
            "octave_candidate" to t.octaveCandidate,
            "octave_applied" to t.octaveApplied,
            "final_lag" to t.finalLag,
            "refined_lag" to t.refinedLag,
            "frequency_hz" to t.frequencyHz,
            "confidence" to 1f - t.cmnd[t.finalLag],
            "parabola" to t.parabola!!.joinToString(" ")
        )
        File(out, "$name.meta").writeText(meta.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })
    }

    /**
     * d(τ) of a sine with amplitude 0.5 at fractional lags within ±8 samples of its period. The shifted signal is
     * computed from the formula, so this is the curve the whole-sample values are taken from.
     */
    private fun continuous(out: File, name: String, f0: Double) {
        val w = size / 2
        val period = sampleRate / f0
        fun x(t: Double) = 0.5 * sin(2.0 * PI * f0 * t / sampleRate)
        fun d(tau: Double): Double {
            var sum = 0.0
            for (j in 0 until w) {
                val diff = x(j.toDouble()) - x(j + tau)
                sum += diff * diff
            }
            return sum
        }
        // The formula must agree with the detector's difference function at whole lags
        val trace = YinTrace(tone(f0, floatArrayOf(0.5f)))
        val whole = Math.round(period).toInt()
        check(Math.abs(d(whole.toDouble()) - trace.diff[whole]) < 1e-3 * d(period + 4)) { "continuous d differs" }
        val csv = StringBuilder("tau,d\n")
        val steps = 800
        for (i in 0..steps) {
            val tau = period - 8 + 16.0 * i / steps
            csv.append("$tau,${d(tau)}\n")
        }
        File(out, "$name-continuous.csv").writeText(csv.toString())
    }

    /** The 4096 samples starting at [block] × 1024 of a test recording. */
    private fun recording(file: String, block: Int): YinTrace {
        val dir = System.getProperty("samples.dir") ?: error("system property samples.dir is not set")
        val samples = AiffReader.read(File(dir, file)).samples
        val start = block * TunerConfig.HOP_SIZE
        return YinTrace(samples.copyOfRange(start, start + size))
    }

    private fun midiHz(midi: Int) = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    /** Same as in YinPitchDetectorTest: sum of sines at f0, 2·f0, 3·f0 ... with the given amplitudes. */
    private fun tone(f0: Double, harmonics: FloatArray): FloatArray = FloatArray(size) { i ->
        var s = 0.0
        for (h in harmonics.indices) s += harmonics[h] * sin(2.0 * PI * f0 * (h + 1) * i / sampleRate)
        s.toFloat()
    }
}
