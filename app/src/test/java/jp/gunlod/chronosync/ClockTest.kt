package jp.gunlod.chronosync

import jp.gunlod.chronosync.time.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant

class ClockTest {
    private fun utc(text: String): Long = Instant.parse(text).let { it.epochSecond * 1_000_000_000 + it.nano }
    @Test fun unsynchronizedClockNeverFallsBackToDeviceTime() { assertNull(SynchronizedClock { 123L }.nowNanos()) }
    @Test fun advancesWithMonotonicTimeIncludingSleep() {
        var mono = 100L
        val clock = SynchronizedClock { mono }
        clock.synchronize(ClockAnchor(utc("2026-11-21T09:00:00Z"), mono))
        mono += 7_200_000_000_123L
        assertEquals(utc("2026-11-21T11:00:00.000000123Z"), clock.nowNanos())
    }
    @Test fun positiveAndNegativeOffsetsOnlyChangeDisplay() {
        val n = utc("2026-11-21T09:00:00Z")
        assertEquals("18:00:00.001", ClockText.time(ClockText.corrected(n, 1)))
        assertEquals("18:00:00.015", ClockText.time(ClockText.corrected(n, 15)))
        assertEquals("17:59:59.999", ClockText.time(ClockText.corrected(n, -1)))
    }
    @Test fun crossesSecondMinuteAndDateBoundariesInTokyo() {
        listOf(
            "2026-11-21T09:00:00.999Z" to "18:00:01.000",
            "2026-11-21T09:00:59.999Z" to "18:01:00.000",
            "2026-11-21T14:59:59.999Z" to "00:00:00.000",
        ).forEach { (input, output) -> assertEquals(output, ClockText.time(ClockText.corrected(utc(input), 1))) }
        assertEquals("2026/11/22 00:00:00.000", ClockText.date(ClockText.corrected(utc("2026-11-21T14:59:59.999Z"), 1)))
    }
    @Test fun initialFailureRemainsUnsyncedThenSuccessRecovers() = runTest {
        var fail = true
        val sample = NtpSample(ClockAnchor(1234, 100), 50, 30, 25)
        val clock = SynchronizedClock { 200 }
        val manager = ClockSyncManager(NtpSource { if (fail) throw IOException("timeout") else listOf(sample) }, clock)
        manager.synchronize("example.org")
        assertEquals(SyncStatus.UNSYNCED, manager.info.value.status(200))
        assertNull(clock.nowNanos())
        assertNotNull(manager.info.value.lastError)
        fail = false; manager.synchronize("example.org")
        assertEquals(SyncStatus.SYNCED, manager.info.value.status(200))
        assertEquals(1334L, clock.nowNanos())
    }
    @Test fun resyncReplacesAnchorAndFailedResyncRetainsPreviousClock() = runTest {
        var mono = 100L
        var sample = NtpSample(ClockAnchor(1_000_000, 100), 10, 0, 10)
        var fail = false
        val clock = SynchronizedClock { mono }
        val manager = ClockSyncManager(NtpSource { if (fail) throw IOException("offline") else listOf(sample) }, clock)
        manager.synchronize("first")
        mono = 200
        sample = sample.copy(anchor = ClockAnchor(2_000_000, 200))
        manager.synchronize("second")
        mono = 250
        assertEquals(2_000_050L, clock.nowNanos())
        fail = true; manager.synchronize("unreachable")
        assertEquals(2_000_050L, clock.nowNanos())
        assertEquals(SyncStatus.DEGRADED, manager.info.value.status(mono))
        assertEquals("second", manager.info.value.server)
    }
    @Test fun automaticStartupSyncRunsOnlyOncePerProcess() = runTest {
        var count = 0
        val manager = ClockSyncManager(NtpSource { count++; throw IOException("offline") }, SynchronizedClock { 0 })
        repeat(3) { manager.synchronize("host", true) }
        assertEquals(1, count)
        manager.synchronize("host")
        assertEquals(2, count)
    }
    @Test fun choosesLowRttReliableSampleAndDegradesWithAge() = runTest {
        val slow = NtpSample(ClockAnchor(1, 0), 100_000_000, 0, 50_000_000)
        val fast = NtpSample(ClockAnchor(2, 0), 2_000_000, 0, 1_000_000)
        val manager = ClockSyncManager(NtpSource { listOf(slow, fast) }, SynchronizedClock { 0 })
        manager.synchronize("host")
        assertEquals(fast, manager.info.value.sample)
        assertEquals(SyncStatus.SYNCED, manager.info.value.status(0))
        assertEquals(SyncStatus.DEGRADED, manager.info.value.status(3_600_000_000_000))
        assertTrue(manager.info.value.uncertaintyNanos(3_600_000_000_000)!! > fast.uncertaintyNanos)
    }
}
