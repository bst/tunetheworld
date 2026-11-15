package io.github.bst.tunetheworld.dsp

import io.github.bst.tunetheworld.engine.TunerConfig
import kotlin.math.abs
import kotlin.math.log2

/** Median smoothing with octave-jump protection. Allocates only in the constructor. */
class PitchSmoother(private val size: Int = TunerConfig.MEDIAN_SIZE) {
    private val ring = FloatArray(size)
    private val sorted = FloatArray(size)
    private var count = 0
    private var next = 0
    private var jumpCount = 0
    private var lastJump = 0f

    fun add(freqHz: Float): Float {
        if (count == 0) {
            push(freqHz)
            return freqHz
        }
        val median = median()
        if (semitones(freqHz, median) > JUMP_SEMITONES) {
            // Outliers only count as a pitch change when they agree with each other
            jumpCount = if (jumpCount > 0 && semitones(freqHz, lastJump) <= JUMP_SEMITONES) jumpCount + 1 else 1
            lastJump = freqHz
            if (jumpCount >= JUMPS_FOR_CHANGE) {
                reset()
                push(freqHz)
                return freqHz
            }
            return median
        }
        jumpCount = 0
        push(freqHz)
        return median()
    }

    fun reset() {
        count = 0
        next = 0
        jumpCount = 0
    }

    private fun push(value: Float) {
        ring[next] = value
        next = (next + 1) % size
        if (count < size) count++
    }

    private fun median(): Float {
        for (i in 0 until count) sorted[i] = ring[i]
        // Insertion sort, no allocation
        for (i in 1 until count) {
            val v = sorted[i]
            var j = i - 1
            while (j >= 0 && sorted[j] > v) {
                sorted[j + 1] = sorted[j]
                j--
            }
            sorted[j + 1] = v
        }
        return if (count % 2 == 1) sorted[count / 2] else 0.5f * (sorted[count / 2 - 1] + sorted[count / 2])
    }

    private fun semitones(a: Float, b: Float): Float = abs(12f * log2(a / b))

    private companion object {
        const val JUMP_SEMITONES = 0.8f
        const val JUMPS_FOR_CHANGE = 3
    }
}
