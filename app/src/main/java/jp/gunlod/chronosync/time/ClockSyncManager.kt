package jp.gunlod.chronosync.time

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

enum class SyncStatus { SYNCED, DEGRADED, UNSYNCED }
data class SyncInfo(
    val sample: NtpSample? = null, val server: String = "ntp.nict.jp",
    val syncing: Boolean = false, val lastError: String? = null,
) {
    fun ageNanos(now: Long): Long = sample?.let { (now - it.anchor.monotonicNanos).coerceAtLeast(0) } ?: 0
    // 20 ppm is a heuristic drift allowance, not a measured or guaranteed oscillator bound.
    fun uncertaintyNanos(now: Long): Long? = sample?.let { it.uncertaintyNanos + ageNanos(now) / 50_000 }
    fun status(now: Long): SyncStatus = when {
        sample == null -> SyncStatus.UNSYNCED
        lastError != null || ageNanos(now) >= 3_600_000_000_000L || (uncertaintyNanos(now) ?: 0) > 100_000_000L -> SyncStatus.DEGRADED
        else -> SyncStatus.SYNCED
    }
}

class ClockSyncManager(private val source: NtpSource, val clock: SynchronizedClock) {
    private val mutableInfo = MutableStateFlow(SyncInfo())
    val info = mutableInfo.asStateFlow()
    private val mutex = Mutex()
    private var attempted = false
    suspend fun synchronize(server: String, onlyIfNeverAttempted: Boolean = false) {
        if (!mutex.tryLock()) return
        try {
            if (onlyIfNeverAttempted && attempted) return
            attempted = true
            mutableInfo.value = mutableInfo.value.copy(
                server = if (mutableInfo.value.sample == null) server else mutableInfo.value.server,
                syncing = true, lastError = null,
            )
            try {
                val samples = source.measure(server)
                val best = samples.minByOrNull { it.rttNanos + it.uncertaintyNanos } ?: error("有効な測定がありません")
                clock.synchronize(best.anchor)
                mutableInfo.value = SyncInfo(best, server)
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) {
                mutableInfo.value = mutableInfo.value.copy(
                    server = if (mutableInfo.value.sample == null) server else mutableInfo.value.server,
                    syncing = false, lastError = "$server: ${e.localizedMessage ?: "同期失敗"}",
                )
            }
        } finally {
            mutableInfo.value = mutableInfo.value.copy(syncing = false)
            mutex.unlock()
        }
    }
}
