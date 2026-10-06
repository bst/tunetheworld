package de.example.saxtuner.dsp

import kotlin.math.sqrt

/** Root mean square of `length` samples of [buffer] starting at [from]. */
fun rms(buffer: FloatArray, from: Int, length: Int): Float {
    if (length <= 0) return 0f
    var sum = 0f
    for (i in from until from + length) sum += buffer[i] * buffer[i]
    return sqrt(sum / length)
}
