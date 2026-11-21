package jp.gunlod.chronosync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.gunlod.chronosync.data.AppSettings
import jp.gunlod.chronosync.overlay.OverlayClockService
import jp.gunlod.chronosync.time.SyncInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

data class MainUiState(
    val settings: AppSettings = AppSettings(), val sync: SyncInfo = SyncInfo(),
    val running: Boolean = false, val ready: Boolean = false, val saveError: String? = null,
    val monotonicNow: Long = 0,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as ChronoSyncApp
    private val values = combine(app.settings.settings, app.sync.info, OverlayClockService.running, app.settings.ready, app.settings.error) { s, sync, running, ready, error ->
        MainUiState(s, sync, running, ready, error)
    }
    private val ticker = flow { while (true) { emit(app.monotonic.nanos()); delay(1000) } }
    val ui = combine(values, ticker) { state, now -> state.copy(monotonicNow = now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())
    init { app.initialSync() }
    fun update(change: (AppSettings) -> AppSettings) = app.settings.update(change)
}
