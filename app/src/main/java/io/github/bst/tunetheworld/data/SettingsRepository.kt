package io.github.bst.tunetheworld.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.bst.tunetheworld.engine.TunerConfig
import io.github.bst.tunetheworld.music.Transposition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<TunerSettings> = store.data.map { prefs ->
        TunerSettings(
            referenceHz = (prefs[REFERENCE_HZ] ?: DEFAULT_REFERENCE_HZ).coerceIn(MIN_REFERENCE_HZ, MAX_REFERENCE_HZ),
            transposition = Transposition.clamp(prefs[TRANSPOSITION] ?: Transposition.CONCERT),
            gateDb = (prefs[GATE_DB] ?: TunerConfig.DEFAULT_GATE_DB)
                .coerceIn(TunerConfig.MIN_GATE_DB, TunerConfig.MAX_GATE_DB)
        )
    }

    suspend fun setReferenceHz(hz: Int) {
        store.edit { it[REFERENCE_HZ] = hz.coerceIn(MIN_REFERENCE_HZ, MAX_REFERENCE_HZ) }
    }

    suspend fun setTransposition(semitones: Int) {
        store.edit { it[TRANSPOSITION] = Transposition.clamp(semitones) }
    }

    suspend fun setGateDb(db: Float) {
        store.edit { it[GATE_DB] = db.coerceIn(TunerConfig.MIN_GATE_DB, TunerConfig.MAX_GATE_DB) }
    }

    companion object {
        const val MIN_REFERENCE_HZ = 400
        const val MAX_REFERENCE_HZ = 480
        const val DEFAULT_REFERENCE_HZ = 440
        private val REFERENCE_HZ = intPreferencesKey("reference_hz")
        private val GATE_DB = floatPreferencesKey("gate_db")
        private val TRANSPOSITION = intPreferencesKey("transposition_semitones")
    }
}
