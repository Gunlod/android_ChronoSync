package jp.gunlod.chronosync

import android.app.Application
import android.os.SystemClock
import androidx.datastore.preferences.preferencesDataStore
import jp.gunlod.chronosync.data.SettingsRepository
import jp.gunlod.chronosync.time.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val Application.dataStore by preferencesDataStore("chronosync")

class ChronoSyncApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val monotonic = MonotonicClock { SystemClock.elapsedRealtimeNanos() }
    val clock = SynchronizedClock(monotonic)
    val sync = ClockSyncManager(NtpClient(monotonic), clock)
    lateinit var settings: SettingsRepository
        private set
    lateinit var offsets: ClockOffsetManager
        private set
    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(dataStore, scope)
        offsets = ClockOffsetManager(settings)
    }
    fun initialSync() { scope.launch { settings.awaitLoaded(); sync.synchronize(settings.settings.value.server, true) } }
    fun resync() { scope.launch { settings.awaitLoaded(); sync.synchronize(settings.settings.value.server) } }
}
