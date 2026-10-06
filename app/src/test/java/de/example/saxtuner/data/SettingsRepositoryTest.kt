package de.example.saxtuner.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.example.saxtuner.music.Transposition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

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
        assertEquals(TunerSettings(440, Transposition.TENOR_BB), repo.settings.first())
    }

    @Test
    fun persistsValues() = runBlocking {
        repo.setReferenceHz(442)
        repo.setTransposition(Transposition.ALTO_EB)
        assertEquals(TunerSettings(442, Transposition.ALTO_EB), repo.settings.first())
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
    fun unknownTranspositionFallsBackToTenor() = runBlocking {
        store.edit { it[stringPreferencesKey("transposition")] = "SOPRANO" }
        assertEquals(Transposition.TENOR_BB, repo.settings.first().transposition)
    }
}
