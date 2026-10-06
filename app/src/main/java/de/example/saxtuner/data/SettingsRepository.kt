package de.example.saxtuner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.example.saxtuner.music.Transposition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<TunerSettings> = store.data.map { prefs ->
        TunerSettings(
            referenceHz = (prefs[REFERENCE_HZ] ?: DEFAULT_REFERENCE_HZ).coerceIn(MIN_REFERENCE_HZ, MAX_REFERENCE_HZ),
            transposition = prefs[TRANSPOSITION]
                ?.let { name -> Transposition.entries.firstOrNull { it.name == name } }
                ?: Transposition.TENOR_BB
        )
    }

    suspend fun setReferenceHz(hz: Int) {
        store.edit { it[REFERENCE_HZ] = hz.coerceIn(MIN_REFERENCE_HZ, MAX_REFERENCE_HZ) }
    }

    suspend fun setTransposition(t: Transposition) {
        store.edit { it[TRANSPOSITION] = t.name }
    }

    companion object {
        const val MIN_REFERENCE_HZ = 400
        const val MAX_REFERENCE_HZ = 480
        const val DEFAULT_REFERENCE_HZ = 440
        private val REFERENCE_HZ = intPreferencesKey("reference_hz")
        private val TRANSPOSITION = stringPreferencesKey("transposition")
    }
}
