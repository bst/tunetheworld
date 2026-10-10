package de.example.saxtuner.ui

import de.example.saxtuner.data.TunerSettings
import de.example.saxtuner.engine.TunerConfig
import de.example.saxtuner.music.NoteResult

data class TunerUiState(
    val note: NoteResult? = null, // null = no tone detected
    val isActive: Boolean = false, // true while a tone is sounding
    val levelDb: Float = TunerConfig.METER_FLOOR_DB, // smoothed input level for the meter
    val settings: TunerSettings = TunerSettings()
)
