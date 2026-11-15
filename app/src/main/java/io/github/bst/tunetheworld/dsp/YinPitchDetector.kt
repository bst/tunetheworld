package io.github.bst.tunetheworld.dsp

import kotlin.math.ceil
import kotlin.math.floor

/**
 * YIN pitch detector with a few additions, see PITCH_DETECTION.md. Pure Kotlin; all arrays are allocated once,
 * [detect] allocates nothing but its result.
 *
 * Not thread-safe, and not stateless: [detect] remembers the previous block's period (step 6). Feed it consecutive
 * blocks of one signal and call [reset] when the signal is interrupted, for example when the level gate closes.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    windowSize: Int,
    minFreqHz: Float,
    maxFreqHz: Float,
    private val threshold: Float = 0.20f
) {
    private val w = windowSize / 2 // integration window
    private val tauMin = floor(sampleRate / maxFreqHz).toInt().coerceAtLeast(2)
    private val tauMax = ceil(sampleRate / minFreqHz).toInt().coerceAtMost(w - 2)
    private val difference = DifferenceFunction(w, tauMax + 1)
    private val diff = FloatArray(tauMax + 2)
    private val cmnd = FloatArray(tauMax + 2)

    // Step 6: the previous block's lag and the depth of its dip in d′, or -1 if there is none
    private var prevLag = -1
    private var prevDepth = 0f

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
        var found = firstDipBelow(accept, tauMin, tauMax)
        if (found == -1 && prevLag == -1) return null

        // Step 3b: octave check. When the 2nd (or 3rd) harmonic is much stronger than the fundamental (low notes, notes
        // dying away, the oboe's B♭4), YIN finds a half (or third) of the period first. If the raw difference at twice
        // (three times) the lag is much smaller, that is the real period. A clean tone has all dips near zero, so their
        // ratio means nothing there; the first dip must be noticeably above zero before the check applies.
        // At short lags the period falls between whole samples, and a multiple can land on a whole sample by chance
        // and look deeper (violin E7: period 16.7, lag 16 shallow, lag 33 deep). Comparing the interpolated depths of
        // the dips makes this safe for three times the lag down to lag 20, but not for twice the lag, which keeps its
        // limit of 100 (docs/research/RESEARCH_LOG.md, experiments 1 and 8).
        if (found >= TRIPLE_MIN_LAG && cmnd[found] > OCTAVE_MIN_DIP) {
            val base = dipDepth(found)
            for (k in 2..3) {
                if (found < (if (k == 2) DOUBLE_MIN_LAG else TRIPLE_MIN_LAG)) continue
                val multiple = deepestNear(k * found)
                if (multiple == -1) break
                if (dipDepth(multiple) < OCTAVE_RATIO * base) {
                    found = multiple
                    break
                }
            }
        }

        // Step 6 of the paper ("best local estimate"), with the previous block as the neighbourhood: if its dip was
        // cleaner, search again within ±20 % of its period. Only move there if the raw difference is much smaller
        // there, as at the true period of a low string whose fundamental fades. Without that guard an octave-low
        // reading would be carried on, because a tone that repeats after T also repeats after 2T (experiment 5).
        val current = if (found == -1) Float.MAX_VALUE else cmnd[found]
        if (prevLag != -1 && current > 0f && prevDepth < current) {
            val lo = maxOf(tauMin, (prevLag * 0.8f).toInt())
            val hi = minOf(tauMax, ceil(prevLag * 1.2f).toInt())
            var pick = firstDipBelow(threshold, lo, hi)
            if (pick == -1) {
                pick = lo
                for (t in lo..hi) if (cmnd[t] < cmnd[pick]) pick = t
            }
            if (found == -1 || diff[pick] < LOCAL_RATIO * diff[found]) found = pick
        }
        prevLag = found
        prevDepth = if (found == -1) 0f else cmnd[found]
        if (found == -1) return null

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

    /** Forgets the previous block, e.g. when the level gate closes between two notes. */
    fun reset() {
        prevLag = -1
    }

    /** First lag in [from]..[to] where d′ drops below [limit], walked down to the bottom of that dip; -1 if none. */
    private fun firstDipBelow(limit: Float, from: Int, to: Int): Int {
        var tau = from
        while (tau <= to) {
            if (cmnd[tau] < limit) {
                while (tau + 1 <= to && cmnd[tau + 1] < cmnd[tau]) tau++
                return tau
            }
            tau++
        }
        return -1
    }

    /** Depth of the raw difference's dip at [t], from a parabola through it and its neighbours. */
    private fun dipDepth(t: Int): Float {
        val a = diff[t - 1]
        val b = diff[t]
        val c = diff[t + 1]
        val den = a - 2f * b + c
        return if (den <= 0f) b else maxOf(0f, b - (a - c) * (a - c) / (8f * den))
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
        const val OCTAVE_MIN_DIP = 0.02f // a first dip deeper than this is trusted as it is
        const val DOUBLE_MIN_LAG = 100 // samples; twice the lag is checked below about 440 Hz
        const val TRIPLE_MIN_LAG = 20 // three times the lag is checked for fundamentals below about 735 Hz
        const val OCTAVE_RATIO = 0.2f // the dip at the multiple must be this much smaller
        const val LOCAL_RATIO = 0.3f // step 6 moves only if the raw difference is this much smaller there
    }
}
