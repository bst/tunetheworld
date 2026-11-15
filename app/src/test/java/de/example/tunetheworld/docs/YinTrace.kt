package de.example.tunetheworld.docs

import de.example.tunetheworld.dsp.DifferenceFunction
import de.example.tunetheworld.dsp.YinPitchDetector
import de.example.tunetheworld.engine.TunerConfig
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Repeats the steps of [YinPitchDetector] and keeps every intermediate value, for the plots in PITCH_DETECTION.md.
 * [checkAgainstDetector] makes sure the copy still matches the real detector. Like a fresh detector, it has no
 * previous block, so step 6 never applies.
 */
class YinTrace(val buffer: FloatArray) {
    val sampleRate = TunerConfig.SAMPLE_RATE
    val w = TunerConfig.WINDOW_SIZE / 2
    val tauMin = floor(sampleRate / TunerConfig.MAX_FREQ_HZ).toInt().coerceAtLeast(2)
    val tauMax = ceil(sampleRate / TunerConfig.MIN_FREQ_HZ).toInt().coerceAtMost(w - 2)
    val threshold = TunerConfig.YIN_THRESHOLD

    /** Step 1 and 2: d(τ) and d′(τ) for τ = 0..tauMax + 1. */
    val diff = FloatArray(tauMax + 2).also { DifferenceFunction(w, tauMax + 1).compute(buffer, it) }
    val cmnd = FloatArray(tauMax + 2)

    init {
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax + 1) {
            running += diff[tau]
            cmnd[tau] = if (running == 0f) 1f else diff[tau] * tau / running
        }
    }

    /** Step 3. */
    val deepest = (tauMin..tauMax).minOf { cmnd[it] }
    val accept = minOf(threshold, deepest + NEAR_DEEPEST)

    /** What plain YIN would pick: the first dip below the threshold. */
    val plainLag = firstDipBelow(threshold)

    /** First dip below [accept], also close to the deepest dip. */
    val nearDeepestLag = firstDipBelow(accept)

    /** Step 3b: the multiple of [nearDeepestLag] the octave check moved to, or -1 if it kept the lag. */
    val octaveCandidate = octaveCheck(nearDeepestLag)
    val octaveApplied = octaveCandidate != -1
    val finalLag = if (octaveApplied) octaveCandidate else nearDeepestLag

    /** Step 4: the parabola a·(τ − finalLag)² + b·(τ − finalLag) + c through raw d at the dip and its neighbours. */
    val parabola: DoubleArray? = if (finalLag == -1) {
        null
    } else {
        val s0 = diff[finalLag - 1].toDouble()
        val s1 = diff[finalLag].toDouble()
        val s2 = diff[finalLag + 1].toDouble()
        doubleArrayOf((s0 - 2 * s1 + s2) / 2, (s2 - s0) / 2, s1)
    }
    val refinedLag: Float = if (finalLag == -1) Float.NaN else vertex(diff)

    /** The same parabola fitted to d′ instead, as the detector did before following the paper. For comparison. */
    val refinedLagCmnd: Float = if (finalLag == -1) Float.NaN else vertex(cmnd)
    val frequencyHz: Float get() = sampleRate / refinedLag

    fun checkAgainstDetector() {
        val detector = YinPitchDetector(
            sampleRate,
            TunerConfig.WINDOW_SIZE,
            TunerConfig.MIN_FREQ_HZ,
            TunerConfig.MAX_FREQ_HZ,
            threshold
        )
        val real = detector.detect(buffer)?.frequencyHz
        val mine = if (finalLag == -1) null else frequencyHz
        check(real == mine) { "YinTrace no longer matches YinPitchDetector: $mine Hz instead of $real Hz" }
    }

    private fun vertex(v: FloatArray): Float {
        val s0 = v[finalLag - 1]
        val s1 = v[finalLag]
        val s2 = v[finalLag + 1]
        val denom = s0 - 2f * s1 + s2
        return if (denom <= 0f) finalLag.toFloat() else finalLag + 0.5f * (s0 - s2) / denom
    }

    private fun firstDipBelow(limit: Float): Int {
        var tau = tauMin
        while (tau <= tauMax) {
            if (cmnd[tau] < limit) {
                while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                return tau
            }
            tau++
        }
        return -1
    }

    private fun octaveCheck(found: Int): Int {
        if (found < TRIPLE_MIN_LAG || cmnd[found] <= OCTAVE_MIN_DIP) return -1
        val base = dipDepth(found)
        for (k in 2..3) {
            if (found < (if (k == 2) DOUBLE_MIN_LAG else TRIPLE_MIN_LAG)) continue
            val multiple = deepestNear(k * found)
            if (multiple == -1) return -1
            if (dipDepth(multiple) < OCTAVE_RATIO * base) return multiple
        }
        return -1
    }

    private fun dipDepth(t: Int): Float {
        val a = diff[t - 1]
        val b = diff[t]
        val c = diff[t + 1]
        val den = a - 2f * b + c
        return if (den <= 0f) b else maxOf(0f, b - (a - c) * (a - c) / (8f * den))
    }

    private fun deepestNear(center: Int): Int {
        if (center + 2 > tauMax) return -1
        var best = center - 2
        for (t in center - 1..center + 2) if (diff[t] < diff[best]) best = t
        return best
    }

    private companion object {
        // Copies of the private constants in YinPitchDetector; checkAgainstDetector catches a mismatch
        const val NEAR_DEEPEST = 0.1f
        const val OCTAVE_MIN_DIP = 0.02f
        const val DOUBLE_MIN_LAG = 100
        const val TRIPLE_MIN_LAG = 20
        const val OCTAVE_RATIO = 0.2f
    }
}
