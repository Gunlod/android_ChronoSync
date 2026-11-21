package jp.gunlod.chronosync.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun interface MonotonicClock { fun nanos(): Long }

data class ClockAnchor(val utcNanos: Long, val monotonicNanos: Long)

class SynchronizedClock(private val monotonic: MonotonicClock) {
    @Volatile private var anchor: ClockAnchor? = null
    fun synchronize(value: ClockAnchor) { anchor = value }
    fun nowNanos(): Long? = anchor?.let { it.utcNanos + (monotonic.nanos() - it.monotonicNanos) }
}

object ClockText {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.of("Asia/Tokyo"))
    private val full = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS").withZone(ZoneId.of("Asia/Tokyo"))
    fun instant(nanos: Long): Instant = Instant.ofEpochSecond(Math.floorDiv(nanos, 1_000_000_000L), Math.floorMod(nanos, 1_000_000_000L))
    fun time(nanos: Long): String = formatter.format(instant(nanos))
    fun date(nanos: Long): String = full.format(instant(nanos))
    fun corrected(nanos: Long, offsetMs: Int): Long = nanos + offsetMs.toLong() * 1_000_000L
    fun offset(ms: Int): String = (if (ms >= 0) "+" else "") + ms + " ms"
}
