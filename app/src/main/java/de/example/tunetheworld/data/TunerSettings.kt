package de.example.tunetheworld.data

import de.example.tunetheworld.engine.TunerConfig
import de.example.tunetheworld.music.Transposition

data class TunerSettings(
    val referenceHz: Int = 440,
    /** Semitones the displayed note lies above the sounding note, see [Transposition]. */
    val transposition: Int = Transposition.CONCERT,
    val gateDb: Float = TunerConfig.DEFAULT_GATE_DB
)
