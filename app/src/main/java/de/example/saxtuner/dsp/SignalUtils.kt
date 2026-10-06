package de.example.saxtuner.dsp

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** Root mean square of `length` samples of [buffer] starting at [from]. */
fun rms(buffer: FloatArray, from: Int, length: Int): Float {
    if (length <= 0) return 0f
    var sum = 0f
    for (i in from until from + length) sum += buffer[i] * buffer[i]
    return sqrt(sum / length)
}

/** Converts a linear amplitude to dBFS; silence maps to [floorDb]. */
fun amplitudeToDb(amplitude: Float, floorDb: Float): Float =
    if (amplitude <= 0f) floorDb else maxOf(floorDb, 20f * log10(amplitude))

/** Converts dBFS to a linear amplitude. */
fun dbToAmplitude(db: Float): Float = 10f.pow(db / 20f)
