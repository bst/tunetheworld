package de.example.saxtuner.data

import de.example.saxtuner.engine.TunerConfig
import de.example.saxtuner.music.Transposition

data class TunerSettings(
    val referenceHz: Int = 440,
    val transposition: Transposition = Transposition.TENOR_BB,
    val gateDb: Float = TunerConfig.DEFAULT_GATE_DB
)
