package jp.gunlod.chronosync.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class AppSettings(
    val server: String = "ntp.nict.jp", val offsetMs: Int = 0, val stepMs: Int = 1,
    val textSizeSp: Float = 18f, val textColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundOpacity: Float = 0.8f, val x: Int = 24, val y: Int = 160,
    val overlayEnabled: Boolean = false,
) {
    fun sanitized() = copy(
        server = server.trim().ifEmpty { "ntp.nict.jp" }, offsetMs = offsetMs.coerceIn(-9999, 9999),
        stepMs = if (stepMs in listOf(1, 10, 100)) stepMs else 1,
        textSizeSp = if (textSizeSp.isFinite()) textSizeSp.coerceIn(12f, 36f) else 18f,
        backgroundOpacity = if (backgroundOpacity.isFinite()) backgroundOpacity.coerceIn(0.1f, 1f) else 0.8f,
        x = x.coerceAtLeast(0), y = y.coerceAtLeast(0),
    )
}

object SettingsCodec {
    private val server = stringPreferencesKey("server")
    private val offset = intPreferencesKey("offset_ms")
    private val step = intPreferencesKey("step_ms")
    private val size = floatPreferencesKey("text_sp")
    private val color = intPreferencesKey("text_color")
    private val opacity = floatPreferencesKey("background_opacity")
    private val x = intPreferencesKey("x")
    private val y = intPreferencesKey("y")
    private val overlay = booleanPreferencesKey("overlay_enabled")
    fun decode(p: Preferences): AppSettings = AppSettings(
        p[server] ?: "ntp.nict.jp", p[offset] ?: 0, p[step] ?: 1,
        p[size] ?: 18f, p[color] ?: 0xFFFFFFFF.toInt(), p[opacity] ?: 0.8f, p[x] ?: 24, p[y] ?: 160,
        p[overlay] ?: false,
    ).sanitized()
    fun encode(s: AppSettings, p: MutablePreferences) {
        p[server] = s.server; p[offset] = s.offsetMs; p[step] = s.stepMs
        p[size] = s.textSizeSp; p[color] = s.textColor; p[opacity] = s.backgroundOpacity
        p[x] = s.x; p[y] = s.y
        p[overlay] = s.overlayEnabled
    }
}

class SettingsRepository(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val mutableSettings = MutableStateFlow(AppSettings())
    val settings = mutableSettings.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val loaded = CompletableDeferred<Unit>()
    // Each value is a full snapshot. Keep the latest pending value so a rapid slider/offset
    // gesture cannot build a long queue of obsolete fsync operations.
    private val saves = Channel<AppSettings>(Channel.CONFLATED)
    init {
        scope.launch {
            try {
                // Keep one storage observer alive. In particular, do not tear down DataStore's
                // notification subscription between initialization and the first queued write.
                store.data.collect { preferences ->
                    if (!loaded.isCompleted) {
                        mutableSettings.value = SettingsCodec.decode(preferences)
                        mutableReady.value = true
                        loaded.complete(Unit)
                    }
                }
            } catch (e: IOException) {
                mutableError.value = "設定の読み込みに失敗しました: ${e.message}"
                mutableReady.value = true
                loaded.complete(Unit)
            }
        }
        scope.launch {
            loaded.await()
            for (value in saves) {
                try { store.edit { SettingsCodec.encode(value, it) }; mutableError.value = null }
                catch (e: IOException) { mutableError.value = "設定を保存できません: ${e.message}" }
            }
        }
    }
    suspend fun awaitLoaded() { loaded.await() }
    @Synchronized fun update(transform: (AppSettings) -> AppSettings) {
        if (!mutableReady.value) return
        val next = transform(mutableSettings.value).sanitized()
        mutableSettings.value = next // Immediate display response; disk writes are ordered in one channel.
        saves.trySend(next)
    }
}
