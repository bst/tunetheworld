package de.example.tunetheworld.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.example.tunetheworld.music.Transposition
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File.createTempFile("settings", ".preferences_pb").also { it.delete() }
    private val store = PreferenceDataStoreFactory.create(scope = scope) { file }
    private val repo = SettingsRepository(store)

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun defaults() = runBlocking {
        assertEquals(TunerSettings(440, Transposition.CONCERT), repo.settings.first())
    }

    @Test
    fun persistsValues() = runBlocking {
        repo.setReferenceHz(442)
        repo.setTransposition(-3)
        assertEquals(TunerSettings(442, -3), repo.settings.first())
    }

    @Test
    fun persistsAndClampsGate() = runBlocking {
        repo.setGateDb(-50f)
        assertEquals(-50f, repo.settings.first().gateDb)
        repo.setGateDb(-10f)
        assertEquals(-30f, repo.settings.first().gateDb)
        repo.setGateDb(-90f)
        assertEquals(-60f, repo.settings.first().gateDb)
    }

    @Test
    fun clampsReference() = runBlocking {
        repo.setReferenceHz(500)
        assertEquals(480, repo.settings.first().referenceHz)
        repo.setReferenceHz(300)
        assertEquals(400, repo.settings.first().referenceHz)
    }

    @Test
    fun clampsTransposition() = runBlocking {
        repo.setTransposition(40)
        assertEquals(Transposition.MAX, repo.settings.first().transposition)
        repo.setTransposition(-40)
        assertEquals(Transposition.MIN, repo.settings.first().transposition)
    }

    @Test
    fun migratesVersion1Modes() = runBlocking {
        store.edit { it[stringPreferencesKey("transposition")] = "TENOR_BB" }
        assertEquals(14, repo.settings.first().transposition)
        store.edit { it[stringPreferencesKey("transposition")] = "ALTO_EB" }
        assertEquals(9, repo.settings.first().transposition)
        repo.setTransposition(2)
        assertEquals(2, repo.settings.first().transposition)
    }

    @Test
    fun unknownVersion1ModeFallsBackToConcert() = runBlocking {
        store.edit { it[stringPreferencesKey("transposition")] = "SOPRANO" }
        assertEquals(Transposition.CONCERT, repo.settings.first().transposition)
    }
}
