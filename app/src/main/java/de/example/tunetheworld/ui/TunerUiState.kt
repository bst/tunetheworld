package de.example.tunetheworld.ui

import de.example.tunetheworld.data.TunerSettings
import de.example.tunetheworld.engine.TunerConfig
import de.example.tunetheworld.music.NoteResult

data class TunerUiState(
    val note: NoteResult? = null, // null = no tone detected
    val isActive: Boolean = false, // true while a tone is sounding
    val levelDb: Float = TunerConfig.METER_FLOOR_DB, // smoothed input level for the meter
    val settings: TunerSettings = TunerSettings()
)
