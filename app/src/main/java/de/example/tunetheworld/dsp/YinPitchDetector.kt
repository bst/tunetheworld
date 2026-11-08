package de.example.tunetheworld.dsp

import kotlin.math.ceil
import kotlin.math.floor

/** Switches for experiments (see docs/research/RESEARCH_LOG.md); the defaults are the shipped behaviour. */
internal data class YinOptions(
    val octaveMinDip: Float = 0.02f,
    // 0: off, 1: choose dips by their interpolated depth (paper step 5), 2: also use it for the confidence
    val interpolateDips: Int = 0,
    // 0: off, 1: amplitude-change model (paper Eq. 12) instead of d, 2: only when d gives no confident pitch
    val amplitudeModel: Int = 0,
    // paper step 6, adapted: if the previous block had a cleaner dip, search again within ±20 % of its period
    val bestLocal: Boolean = true,
    val bestLocalMargin: Float = 1f,
    val bestLocalMinDepth: Float = 0f,
    val bestLocalRatio: Float = 0.3f,
    // moving-average low-pass over this many samples before the analysis (paper section IV); 1 = off
    val lowpass: Int = 1,
    val dcModel: Boolean = false,
    // octave check: largest multiple of the lag to try (2 = only twice the lag), the smallest lag it applies to,
    // and whether dips are compared by their interpolated depth instead of the sampled value
    val maxMultiple: Int = 3,
    val octaveMinLag: Int = 100,
    val interpolatedOctave: Boolean = true,
    val tripleMinLag: Int = 20
)

/** YIN pitch detector. Pure Kotlin; all arrays are allocated once, [detect] allocates nothing but its result. */
class YinPitchDetector internal constructor(
    private val sampleRate: Int,
    windowSize: Int,
    minFreqHz: Float,
    maxFreqHz: Float,
    private val threshold: Float,
    private val options: YinOptions
) {
    constructor(sampleRate: Int, windowSize: Int, minFreqHz: Float, maxFreqHz: Float, threshold: Float = 0.15f) :
        this(sampleRate, windowSize, minFreqHz, maxFreqHz, threshold, YinOptions())

    private val w = windowSize / 2 // integration window
    private val tauMin = floor(sampleRate / maxFreqHz).toInt().coerceAtLeast(2)
    private val tauMax = ceil(sampleRate / minFreqHz).toInt().coerceAtMost(w - 2)
    private val difference = DifferenceFunction(w, tauMax + 1).also { it.dcModel = options.dcModel }
    private val diff = FloatArray(tauMax + 2)
    private val cmnd = FloatArray(tauMax + 2)
    private val depth = FloatArray(tauMax + 2)
    private val filtered = FloatArray(windowSize)
    private var prevLag = -1
    private var prevDepth = 0f

    fun detect(buffer: FloatArray): PitchResult? {
        require(buffer.size >= difference.inputSize)
        val first = analyze(buffer, options.amplitudeModel == 1)
        if (options.amplitudeModel == 2 && (first == null || first.confidence < FALLBACK_CONFIDENCE)) {
            return analyze(buffer, true) ?: first
        }
        return first
    }

    private fun analyze(input: FloatArray, amplitudeModel: Boolean): PitchResult? {
        val buffer = if (options.lowpass <= 1) {
            input
        } else {
            var sum = 0f
            for (i in 0 until options.lowpass) sum += input[i]
            for (i in 0..input.size - options.lowpass) {
                filtered[i] = sum / options.lowpass
                if (i + options.lowpass < input.size) sum += input[i + options.lowpass] - input[i]
            }
            filtered
        }
        // Step 1: difference function, computed with an FFT
        difference.compute(buffer, diff, amplitudeModel)

        // Step 2: cumulative mean normalized difference
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax + 1) {
            running += diff[tau]
            cmnd[tau] = if (running == 0f) 1f else diff[tau] * tau / running
        }

        // Experiment: depth of each dip from a parabola through it and its neighbours
        val sel = if (options.interpolateDips > 0) depth else cmnd
        if (options.interpolateDips > 0) {
            for (t in 0..tauMax + 1) depth[t] = cmnd[t]
            for (t in tauMin..tauMax) {
                val a = cmnd[t - 1]
                val b = cmnd[t]
                val c = cmnd[t + 1]
                val den = a - 2f * b + c
                if (b <= a && b <= c && den > 0f) depth[t] = maxOf(0f, b - (a - c) * (a - c) / (8f * den))
            }
        }

        // Step 3: first local minimum below the threshold that is also close to the deepest one. Without the second
        // condition a shallow dip at half the period can win (clarinet B♭6: 0.14 at half the period, 0.01 at the period).
        var deepest = Float.MAX_VALUE
        for (t in tauMin..tauMax) if (sel[t] < deepest) deepest = sel[t]
        val accept = minOf(threshold, deepest + NEAR_DEEPEST)
        var tau = tauMin
        var found = -1
        while (tau <= tauMax) {
            if (sel[tau] < accept) {
                while (tau + 1 <= tauMax && sel[tau + 1] < sel[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found == -1 && !(options.bestLocal && prevLag != -1)) {
            prevLag = -1
            return null
        }

        // Step 3b: octave check. When the second harmonic is much stronger than the fundamental (low notes, and notes
        // dying away), the half period dips below the threshold first. If the dip at twice the lag is clearly deeper,
        // that is the real period. A clean tone has both dips near zero, so their ratio means nothing there; the first
        // dip must be noticeably above zero before the check applies.
        // Only for long lags: at short ones the period falls between whole samples, and twice the period can land on
        // a whole sample by chance and look deeper (violin E7: period 16.7, lag 16 shallow, lag 33 deep).
        if (found >= minOf(options.octaveMinLag, options.tripleMinLag) && cmnd[found] > options.octaveMinDip) {
            val base = if (options.interpolatedOctave) vertexDepth(found) else diff[found]
            for (k in 2..options.maxMultiple) {
                if (found < (if (k == 2) options.octaveMinLag else options.tripleMinLag)) continue
                val multiple = deepestNear(k * found)
                if (multiple == -1) break
                val depth = if (options.interpolatedOctave) vertexDepth(multiple) else diff[multiple]
                if (depth < OCTAVE_RATIO * base) {
                    found = multiple
                    break
                }
            }
        }

        // Experiment: step 6 of the paper, with the previous block as the neighbourhood
        if (options.bestLocal) {
            val current = if (found == -1) Float.MAX_VALUE else cmnd[found]
            if (prevLag != -1 && current > options.bestLocalMinDepth && prevDepth * options.bestLocalMargin < current) {
                val lo = maxOf(tauMin, (prevLag * 0.8f).toInt())
                val hi = minOf(tauMax, ceil(prevLag * 1.2f).toInt())
                var t = lo
                var pick = -1
                while (t <= hi) {
                    if (cmnd[t] < threshold) {
                        while (t + 1 <= hi && cmnd[t + 1] < cmnd[t]) t++
                        pick = t
                        break
                    }
                    t++
                }
                if (pick == -1) {
                    pick = lo
                    for (u in lo..hi) if (cmnd[u] < cmnd[pick]) pick = u
                }
                val keep =
                    options.bestLocalRatio > 0f && found != -1 && diff[pick] >= options.bestLocalRatio * diff[found]
                if (!keep) found = pick
            }
            prevLag = found
            prevDepth = if (found == -1) 0f else cmnd[found]
        }
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
            confidence = (1f - (if (options.interpolateDips == 2) depth[found] else cmnd[found])).coerceIn(0f, 1f)
        )
    }

    /** Depth of the raw difference's dip at [t], from a parabola through it and its neighbours. */
    private fun vertexDepth(t: Int): Float {
        val a = diff[t - 1]
        val b = diff[t]
        val c = diff[t + 1]
        val den = a - 2f * b + c
        return if (den <= 0f) b else maxOf(0f, b - (a - c) * (a - c) / (8f * den))
    }

    /** Forgets the previous block, e.g. when the level gate closes between two notes. */
    fun reset() {
        prevLag = -1
    }

    /** Lag of the smallest value within ±2 of [center], or -1 if that is outside the searched range. */
    private fun deepestNear(center: Int): Int {
        if (center + 2 > tauMax) return -1
        var best = center - 2
        for (t in center - 1..center + 2) if (diff[t] < diff[best]) best = t
        return best
    }

    private companion object {
        const val FALLBACK_CONFIDENCE = 0.8f
        const val NEAR_DEEPEST = 0.1f // how much shallower than the deepest dip the chosen one may be
        const val OCTAVE_RATIO = 0.2f // the raw difference at twice the lag must be this much smaller
    }
}
