package io.github.bst.tunetheworld.data

import io.github.bst.tunetheworld.engine.TunerConfig
import io.github.bst.tunetheworld.music.Transposition

data class TunerSettings(
    val referenceHz: Int = 440,
    /** Semitones the displayed note lies above the sounding note, see [Transposition]. */
    val transposition: Int = Transposition.CONCERT,
    val gateDb: Float = TunerConfig.DEFAULT_GATE_DB
)
