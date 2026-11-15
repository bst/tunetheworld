package de.example.tunetheworld.ui

import android.annotation.SuppressLint
import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.example.tunetheworld.audio.AudioInput
import de.example.tunetheworld.data.SettingsRepository
import de.example.tunetheworld.data.TunerSettings
import de.example.tunetheworld.engine.TunerConfig
import de.example.tunetheworld.engine.TunerEngine
import de.example.tunetheworld.music.NoteMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    private var engine: TunerEngine? = null

    /** Starts listening. The caller must have checked that RECORD_AUDIO is granted. */
    @SuppressLint("MissingPermission")
    @OptIn(FlowPreview::class)
    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                val engine = TunerEngine(AudioInput(getApplication())).also { this@TunerViewModel.engine = it }
                combine(
                    engine.frames().sample(TunerConfig.UI_INTERVAL_MS),
                    repository.settings
                ) { frame, settings -> frame to settings }
                    .collect { (frame, settings) -> update(frame, settings) }
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
        engine = null
        _uiState.value = _uiState.value.copy(isActive = false, levelDb = TunerConfig.METER_FLOOR_DB)
    }

    private fun update(frame: TunerEngine.Frame, settings: TunerSettings) {
        val hz = frame.frequencyHz
        engine?.gateDb = settings.gateDb
        val now = SystemClock.elapsedRealtime()
        val current = _uiState.value
        // Instant attack, slow fall so the meter is readable
        val levelDb = maxOf(frame.levelDb, current.levelDb - TunerConfig.METER_FALL_DB)
        _uiState.value = if (hz != null) {
            lastToneAt = now
            TunerUiState(
                note = NoteMapper.map(hz, settings.referenceHz, settings.transposition),
                isActive = true,
                levelDb = levelDb,
                settings = settings
            )
        } else {
            // Keep the last note, greyed out, until HOLD_MS has passed without a tone
            val expired = now - lastToneAt > TunerConfig.HOLD_MS
            TunerUiState(
                note = if (expired) null else current.note,
                isActive = false,
                levelDb = levelDb,
                settings = settings
            )
        }
    }

    fun setReferenceHz(hz: Int) {
        viewModelScope.launch { repository.setReferenceHz(hz) }
    }

    fun setGateDb(db: Float) {
        viewModelScope.launch { repository.setGateDb(db) }
    }

    fun setTransposition(semitones: Int) {
        viewModelScope.launch { repository.setTransposition(semitones) }
    }

    private companion object {
        const val TAG = "TunerViewModel"
    }
}
