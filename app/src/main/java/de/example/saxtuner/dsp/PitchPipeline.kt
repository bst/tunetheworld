package de.example.saxtuner.dsp

import de.example.saxtuner.engine.TunerConfig

/**
 * Sliding window, level gate, YIN, confidence filter and smoothing as one unit.
 * Android-free so it can be tested on recorded samples. Allocates only in the constructor
 * (apart from the small [PitchResult] returned by YIN).
 */
class PitchPipeline(
    private val detector: YinPitchDetector = YinPitchDetector(
        TunerConfig.SAMPLE_RATE,
        TunerConfig.WINDOW_SIZE,
        TunerConfig.MIN_FREQ_HZ,
        TunerConfig.MAX_FREQ_HZ,
        TunerConfig.YIN_THRESHOLD
    ),
    private val smoother: PitchSmoother = PitchSmoother()
) {
    private val window = FloatArray(TunerConfig.WINDOW_SIZE)
    private var filled = 0

    /** Level gate in dBFS; may be changed from another thread while processing. */
    @Volatile
    var gateDb: Float = TunerConfig.DEFAULT_GATE_DB

    /** RMS level of the most recent block in dBFS (floored at the meter floor). */
    @Volatile
    var levelDb: Float = TunerConfig.METER_FLOOR_DB
        private set

    /** Feeds [length] new samples; returns the smoothed frequency, or null if there is no usable tone. */
    fun process(block: FloatArray, length: Int): Float? {
        levelDb = amplitudeToDb(rms(block, 0, length), TunerConfig.METER_FLOOR_DB)

        // Shift window left by length samples and append the new block at the end
        System.arraycopy(window, length, window, 0, window.size - length)
        System.arraycopy(block, 0, window, window.size - length, length)
        filled = minOf(filled + length, window.size)
        if (filled < window.size) return null

        if (rms(window, window.size - TunerConfig.HOP_SIZE, TunerConfig.HOP_SIZE) < dbToAmplitude(gateDb)) {
            smoother.reset()
            return null
        }
        val result = detector.detect(window) ?: return null
        if (result.confidence < TunerConfig.MIN_CONFIDENCE) return null
        return smoother.add(result.frequencyHz)
    }

    fun reset() {
        filled = 0
        smoother.reset()
        levelDb = TunerConfig.METER_FLOOR_DB
    }
}
