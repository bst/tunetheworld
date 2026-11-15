package io.github.bst.tunetheworld.ui

import io.github.bst.tunetheworld.data.TunerSettings
import io.github.bst.tunetheworld.engine.TunerConfig
import io.github.bst.tunetheworld.music.NoteResult

data class TunerUiState(
    val note: NoteResult? = null, // null = no tone detected
    val isActive: Boolean = false, // true while a tone is sounding
    val levelDb: Float = TunerConfig.METER_FLOOR_DB, // smoothed input level for the meter
    val settings: TunerSettings = TunerSettings()
)
