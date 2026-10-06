package de.example.saxtuner.ui

import de.example.saxtuner.data.TunerSettings
import de.example.saxtuner.music.NoteResult

data class TunerUiState(
    val note: NoteResult? = null,   // null = no tone detected
    val isActive: Boolean = false,  // true while a tone is sounding
    val settings: TunerSettings = TunerSettings()
)
