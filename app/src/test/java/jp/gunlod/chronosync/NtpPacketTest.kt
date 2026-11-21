package jp.gunlod.chronosync

import jp.gunlod.chronosync.time.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class NtpPacketTest {
    private val t1 = Instant.parse("2026-11-21T09:00:00Z").epochSecond * 1_000_000_000
    private val request = NtpPacket.request(t1)
    private fun response(): ByteArray = ByteArray(48).also {
        it[0] = 0x24; it[1] = 1
        request.copyInto(it, 24, 40, 48)
        NtpPacket.writeTimestamp(it, 32, t1 + 30_000_000)
        NtpPacket.writeTimestamp(it, 40, t1 + 32_000_000)
    }
    private fun parse(bytes: ByteArray, length: Int = bytes.size) = NtpPacket.parse(request, bytes, length, t1, 100_000_000, 142_000_000)
    private fun rejected(change: (ByteArray) -> Unit) {
        val b = response(); change(b)
        assertThrows(IllegalArgumentException::class.java) { parse(b) }
    }
    @Test fun calculatesOffsetRttAndReceiveAnchor() {
        val s = parse(response())
        assertEquals(40_000_000.0, s.rttNanos.toDouble(), 2.0)
        assertEquals(10_000_000.0, s.offsetNanos.toDouble(), 2.0)
        assertEquals((t1 + 52_000_000).toDouble(), s.anchor.utcNanos.toDouble(), 256.0)
        assertEquals(142_000_000, s.anchor.monotonicNanos)
        assertTrue(s.uncertaintyNanos >= s.rttNanos / 2)
    }
    @Test fun rejectsTruncatedResponse() { assertThrows(IllegalArgumentException::class.java) { parse(response(), 47) } }
    @Test fun rejectsLeapAlarm() = rejected { it[0] = 0xE4.toByte() }
    @Test fun rejectsWrongMode() = rejected { it[0] = 0x23 }
    @Test fun rejectsWrongVersion() = rejected { it[0] = 0x14 }
    @Test fun rejectsKissOfDeath() = rejected { it[1] = 0 }
    @Test fun rejectsBadStratum() = rejected { it[1] = 16 }
    @Test fun rejectsOriginateMismatch() = rejected { it[24] = (it[24].toInt() xor 1).toByte() }
    @Test fun rejectsZeroTransmitTimestamp() = rejected { it.fill(0, 40, 48) }
    @Test fun rejectsZeroReceiveTimestamp() = rejected { it.fill(0, 32, 40) }
    @Test fun rejectsReversedServerTimestamps() = rejected { NtpPacket.writeTimestamp(it, 40, t1 + 29_000_000) }
    @Test fun rejectsImplausibleProcessingTime() = rejected { NtpPacket.writeTimestamp(it, 40, t1 + 500_000_000) }
    @Test fun rejectsExcessiveDispersion() = rejected { it[8] = 1 }
    @Test fun rejectsNegativeElapsedTime() {
        assertThrows(IllegalArgumentException::class.java) { NtpPacket.parse(request, response(), 48, t1, 2, 1) }
    }
    @Test fun timestampRoundTripsAcrossNtpEraRollover() {
        listOf("2036-02-07T06:28:15Z", "2036-02-07T06:28:17Z", "2026-11-21T09:00:00.123456789Z").forEach {
            val instant = Instant.parse(it)
            val n = instant.epochSecond * 1_000_000_000 + instant.nano
            val b = ByteArray(48); NtpPacket.writeTimestamp(b, 40, n)
            assertTrue(kotlin.math.abs(n - NtpPacket.timestamp(b, 40, n)) <= 1)
        }
    }
    @Test fun negativeOffsetIsSupported() {
        val b = response()
        NtpPacket.writeTimestamp(b, 32, t1 - 10_000_000)
        NtpPacket.writeTimestamp(b, 40, t1 - 8_000_000)
        assertEquals(-30_000_000.0, parse(b).offsetNanos.toDouble(), 2.0)
    }
}
