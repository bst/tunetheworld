package de.example.saxtuner.ui

import android.annotation.SuppressLint
import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.example.saxtuner.audio.AudioInput
import de.example.saxtuner.data.SettingsRepository
import de.example.saxtuner.data.TunerSettings
import de.example.saxtuner.engine.TunerConfig
import de.example.saxtuner.engine.TunerEngine
import de.example.saxtuner.music.NoteMapper
import de.example.saxtuner.music.Transposition
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class TunerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val _uiState = MutableStateFlow(TunerUiState())
    val uiState: StateFlow<TunerUiState> = _uiState.asStateFlow()

    private var job: Job? = null
    private var lastToneAt = 0L

    /** Starts listening. The caller must have checked that RECORD_AUDIO is granted. */
    @SuppressLint("MissingPermission")
    @OptIn(FlowPreview::class)
    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                val engine = TunerEngine(AudioInput(getApplication()))
                combine(
                    engine.frequencies().sample(TunerConfig.UI_INTERVAL_MS),
                    repository.settings
                ) { hz, settings -> hz to settings }
                    .collect { (hz, settings) -> update(hz, settings) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio capture failed", e)
                _uiState.value = _uiState.value.copy(isActive = false)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _uiState.value = _uiState.value.copy(isActive = false)
    }

    private fun update(hz: Float?, settings: TunerSettings) {
        val now = SystemClock.elapsedRealtime()
        val current = _uiState.value
        _uiState.value = if (hz != null) {
            lastToneAt = now
            TunerUiState(
                note = NoteMapper.map(hz, settings.referenceHz, settings.transposition),
                isActive = true,
                settings = settings
            )
        } else {
            // Keep the last note, greyed out, until HOLD_MS has passed without a tone
            val expired = now - lastToneAt > TunerConfig.HOLD_MS
            TunerUiState(
                note = if (expired) null else current.note,
                isActive = false,
                settings = settings
            )
        }
    }

    fun setReferenceHz(hz: Int) {
        viewModelScope.launch { repository.setReferenceHz(hz) }
    }

    fun setTransposition(t: Transposition) {
        viewModelScope.launch { repository.setTransposition(t) }
    }

    private companion object {
        const val TAG = "TunerViewModel"
    }
}
