package de.example.saxtuner.engine

object TunerConfig {
    const val SAMPLE_RATE = 44100       // sampling rate in Hz
    const val WINDOW_SIZE = 4096        // analysis window in samples (about 93 ms)
    const val HOP_SIZE = 1024           // new samples per analysis (about 23 ms)
    const val MIN_FREQ_HZ = 90f         // lower search limit
    const val MAX_FREQ_HZ = 1100f       // upper search limit
    const val YIN_THRESHOLD = 0.15f     // YIN absolute threshold
    const val MIN_CONFIDENCE = 0.80f    // discard results below this
    const val RMS_GATE = 0.01f          // no analysis below this level (about -40 dBFS)
    const val MEDIAN_SIZE = 5           // values used for the median smoothing
    const val HOLD_MS = 800L            // how long the last note stays visible after silence
    const val UI_INTERVAL_MS = 40L      // at most 25 UI updates per second
}
