package de.example.tunetheworld.dsp

import kotlin.math.ceil
import kotlin.math.floor

/** YIN pitch detector. Pure Kotlin; all arrays are allocated once, [detect] allocates nothing but its result. */
class YinPitchDetector(
    private val sampleRate: Int,
    windowSize: Int,
    minFreqHz: Float,
    maxFreqHz: Float,
    private val threshold: Float = 0.15f
) {
    private val w = windowSize / 2 // integration window
    private val tauMin = floor(sampleRate / maxFreqHz).toInt().coerceAtLeast(2)
    private val tauMax = ceil(sampleRate / minFreqHz).toInt().coerceAtMost(w - 2)
    private val difference = DifferenceFunction(w, tauMax + 1)
    private val diff = FloatArray(tauMax + 2)
    private val cmnd = FloatArray(tauMax + 2)

    fun detect(buffer: FloatArray): PitchResult? {
        require(buffer.size >= difference.inputSize)

        // Step 1: difference function, computed with an FFT
        difference.compute(buffer, diff)

        // Step 2: cumulative mean normalized difference
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax + 1) {
            running += diff[tau]
            cmnd[tau] = if (running == 0f) 1f else diff[tau] * tau / running
        }

        // Step 3: first local minimum below the threshold that is also close to the deepest one. Without the second
        // condition a shallow dip at half the period can win (clarinet B♭6: 0.14 at half the period, 0.01 at the period).
        var deepest = Float.MAX_VALUE
        for (t in tauMin..tauMax) if (cmnd[t] < deepest) deepest = cmnd[t]
        val accept = minOf(threshold, deepest + NEAR_DEEPEST)
        var tau = tauMin
        var found = -1
        while (tau <= tauMax) {
            if (cmnd[tau] < accept) {
                while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found == -1) return null

        // Step 3b: octave check. When the second harmonic is much stronger than the fundamental (low notes, and notes
        // dying away), the half period dips below the threshold first. If the dip at twice the lag is clearly deeper,
        // that is the real period. A clean tone has both dips near zero, so their ratio means nothing there; the first
        // dip must be noticeably above zero before the check applies.
        // Only for long lags: at short ones the period falls between whole samples, and twice the period can land on
        // a whole sample by chance and look deeper (violin E7: period 16.7, lag 16 shallow, lag 33 deep).
        if (found >= OCTAVE_MIN_LAG && cmnd[found] > OCTAVE_MIN_DIP) {
            val double = deepestNear(2 * found)
            if (double != -1 && diff[double] < OCTAVE_RATIO * diff[found]) found = double
        }

        // Step 4: parabolic interpolation for sub-sample accuracy. Like the YIN paper, on the raw difference: d′ is d
        // times a factor that grows with τ, which tilts the dip and reads sharp (by about 2 cents above 1 kHz).
        val s0 = diff[found - 1]
        val s1 = diff[found]
        val s2 = diff[found + 1]
        val denom = s0 - 2f * s1 + s2
        val refined = if (denom <= 0f) found.toFloat() else found + 0.5f * (s0 - s2) / denom

        return PitchResult(
            frequencyHz = sampleRate / refined,
            confidence = (1f - cmnd[found]).coerceIn(0f, 1f)
        )
    }

    /** Lag of the smallest value within ±2 of [center], or -1 if that is outside the searched range. */
    private fun deepestNear(center: Int): Int {
        if (center + 2 > tauMax) return -1
        var best = center - 2
        for (t in center - 1..center + 2) if (diff[t] < diff[best]) best = t
        return best
    }

    private companion object {
        const val NEAR_DEEPEST = 0.1f // how much shallower than the deepest dip the chosen one may be
        const val OCTAVE_MIN_LAG = 100 // samples; about 440 Hz at 44.1 kHz
        const val OCTAVE_MIN_DIP = 0.005f // a first dip deeper than this is trusted as it is
        const val OCTAVE_RATIO = 0.2f // the raw difference at twice the lag must be this much smaller
    }
}
