package jp.gunlod.chronosync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import jp.gunlod.chronosync.data.*
import jp.gunlod.chronosync.time.ClockOffsetManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun defaultsAndCorruptRangeValuesAreSanitized() {
        assertEquals(AppSettings(), SettingsCodec.decode(emptyPreferences()))
        val bad = AppSettings(offsetMs = Int.MAX_VALUE, stepMs = 77, textSizeSp = Float.NaN, backgroundOpacity = -2f).sanitized()
        assertEquals(9999, bad.offsetMs); assertEquals(1, bad.stepMs)
        assertEquals(18f, bad.textSizeSp); assertEquals(0.1f, bad.backgroundOpacity)
        assertEquals(-9999, AppSettings(offsetMs = Int.MIN_VALUE).sanitized().offsetMs)
    }
    @Test fun settingsPersistAndRestoreFromRealDataStore() = runBlocking {
        val file = temp.newFolder().resolve("settings.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val repository = SettingsRepository(store, scope)
        try {
            repository.awaitLoaded()
            val expected = AppSettings(server = "time.example.org", offsetMs = -321, stepMs = 100, textSizeSp = 24f, textColor = 0xFFFFE082.toInt(), backgroundOpacity = 0.5f, x = 123, y = 456, overlayEnabled = true)
            repository.update { expected }
            withTimeout(5000) { store.data.first { SettingsCodec.decode(it) == expected } }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val restored = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope2) { file }, scope2)
                restored.awaitLoaded()
                assertEquals(expected, restored.settings.value)
            } finally { scope2.coroutineContext[Job]!!.cancelAndJoin() }
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
    @Test fun offsetClampsAtBothLimitsAndRapidChangesAreNotLost() = runBlocking {
        val file = temp.newFolder().resolve("offset.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            val repository = SettingsRepository(store, scope); repository.awaitLoaded()
            val manager = ClockOffsetManager(repository)
            manager.set(9998); manager.step(10); manager.adjust(1)
            assertEquals(9999, repository.settings.value.offsetMs)
            manager.set(-9998); manager.adjust(-1)
            assertEquals(-9999, repository.settings.value.offsetMs)
            manager.reset(); manager.step(1)
            repeat(40) { manager.adjust(1) }
            assertEquals(40, repository.settings.value.offsetMs)
            withTimeout(5000) { store.data.first { SettingsCodec.decode(it).offsetMs == 40 } }
            manager.set(Int.MAX_VALUE); assertEquals(9999, repository.settings.value.offsetMs)
            manager.set(Int.MIN_VALUE); assertEquals(-9999, repository.settings.value.offsetMs)
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
