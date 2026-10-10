package de.example.saxtuner.engine

object TunerConfig {
    const val SAMPLE_RATE = 44100 // sampling rate in Hz
    const val WINDOW_SIZE = 4096 // analysis window in samples (about 93 ms)
    const val HOP_SIZE = 1024 // new samples per analysis (about 23 ms)
    const val MIN_FREQ_HZ = 90f // lower search limit
    const val MAX_FREQ_HZ = 1100f // upper search limit
    const val YIN_THRESHOLD = 0.15f // YIN absolute threshold
    const val MIN_CONFIDENCE = 0.80f // discard results below this
    const val DEFAULT_GATE_DB = -40f // default level gate: no analysis below this (dBFS)
    const val MIN_GATE_DB = -60f // most sensitive gate setting, for quiet instruments
    const val MAX_GATE_DB = -30f // least sensitive gate setting, for noisy rooms
    const val METER_FLOOR_DB = -70f // level meter shows this as empty
    const val METER_FALL_DB = 3f // meter falls at most this much per UI update
    const val MEDIAN_SIZE = 5 // values used for the median smoothing
    const val HOLD_MS = 800L // how long the last note stays visible after silence
    const val UI_INTERVAL_MS = 40L // at most 25 UI updates per second
}
