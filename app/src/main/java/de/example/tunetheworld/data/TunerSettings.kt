package de.example.tunetheworld.data

import de.example.tunetheworld.engine.TunerConfig
import de.example.tunetheworld.music.Transposition

data class TunerSettings(
    val referenceHz: Int = 440,
    val transposition: Transposition = Transposition.TENOR_BB,
    val gateDb: Float = TunerConfig.DEFAULT_GATE_DB
)
