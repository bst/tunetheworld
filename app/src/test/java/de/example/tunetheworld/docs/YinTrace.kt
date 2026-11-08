package de.example.tunetheworld.docs

import de.example.tunetheworld.dsp.DifferenceFunction
import de.example.tunetheworld.dsp.YinPitchDetector
import de.example.tunetheworld.engine.TunerConfig
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Repeats the steps of [YinPitchDetector] and keeps every intermediate value, for the plots in PITCH_DETECTION.md.
 * [checkAgainstDetector] makes sure the copy still matches the real detector.
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

    /** Step 3b: the lag checked at twice [nearDeepestLag], or -1 if the check does not apply. */
    val octaveCandidate = if (nearDeepestLag >= OCTAVE_MIN_LAG && cmnd[nearDeepestLag] > OCTAVE_MIN_DIP) {
        deepestNear(2 * nearDeepestLag)
    } else {
        -1
    }
    val octaveApplied = octaveCandidate != -1 && diff[octaveCandidate] < OCTAVE_RATIO * diff[nearDeepestLag]
    val finalLag = if (octaveApplied) octaveCandidate else nearDeepestLag

    /** Step 4: the parabola a·(τ − finalLag)² + b·(τ − finalLag) + c through the dip and its neighbours. */
    val parabola: DoubleArray? = if (finalLag == -1) {
        null
    } else {
        val s0 = cmnd[finalLag - 1].toDouble()
        val s1 = cmnd[finalLag].toDouble()
        val s2 = cmnd[finalLag + 1].toDouble()
        doubleArrayOf((s0 - 2 * s1 + s2) / 2, (s2 - s0) / 2, s1)
    }
    val refinedLag: Float = if (finalLag == -1) {
        Float.NaN
    } else {
        val s0 = cmnd[finalLag - 1]
        val s1 = cmnd[finalLag]
        val s2 = cmnd[finalLag + 1]
        val denom = s0 - 2f * s1 + s2
        if (denom == 0f) finalLag.toFloat() else finalLag + 0.5f * (s0 - s2) / denom
    }
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

    private fun deepestNear(center: Int): Int {
        if (center + 2 > tauMax) return -1
        var best = center - 2
        for (t in center - 1..center + 2) if (diff[t] < diff[best]) best = t
        return best
    }

    private companion object {
        // Copies of the private constants in YinPitchDetector; checkAgainstDetector catches a mismatch
        const val NEAR_DEEPEST = 0.1f
        const val OCTAVE_MIN_LAG = 100
        const val OCTAVE_MIN_DIP = 0.005f
        const val OCTAVE_RATIO = 0.2f
    }
}
