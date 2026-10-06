package de.example.saxtuner.dsp

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
    private val w = windowSize / 2                                   // integration window
    private val tauMin = floor(sampleRate / maxFreqHz).toInt().coerceAtLeast(2)
    private val tauMax = ceil(sampleRate / minFreqHz).toInt().coerceAtMost(w - 2)
    private val diff = FloatArray(tauMax + 2)
    private val cmnd = FloatArray(tauMax + 2)

    fun detect(buffer: FloatArray): PitchResult? {
        require(buffer.size >= w + tauMax + 1)

        // Step 1: difference function
        for (tau in 1..tauMax + 1) {
            var sum = 0f
            for (j in 0 until w) {
                val d = buffer[j] - buffer[j + tau]
                sum += d * d
            }
            diff[tau] = sum
        }

        // Step 2: cumulative mean normalized difference
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax + 1) {
            running += diff[tau]
            cmnd[tau] = if (running == 0f) 1f else diff[tau] * tau / running
        }

        // Step 3: first local minimum below the threshold
        var tau = tauMin
        var found = -1
        while (tau <= tauMax) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found == -1) return null

        // Step 4: parabolic interpolation for sub-sample accuracy
        val s0 = cmnd[found - 1]; val s1 = cmnd[found]; val s2 = cmnd[found + 1]
        val denom = s0 - 2f * s1 + s2
        val refined = if (denom == 0f) found.toFloat() else found + 0.5f * (s0 - s2) / denom

        return PitchResult(
            frequencyHz = sampleRate / refined,
            confidence = (1f - s1).coerceIn(0f, 1f)
        )
    }
}
