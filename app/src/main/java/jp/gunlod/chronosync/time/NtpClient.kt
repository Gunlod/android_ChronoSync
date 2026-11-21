package jp.gunlod.chronosync.time

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max

data class NtpSample(
    val anchor: ClockAnchor, val rttNanos: Long, val offsetNanos: Long,
    val uncertaintyNanos: Long,
)

fun interface NtpSource { suspend fun measure(server: String): List<NtpSample> }

/** NTPv3/v4 server response parser, isolated from Android and networking for tests. */
object NtpPacket {
    private const val EPOCH_SECONDS = 2_208_988_800L
    private const val ERA_SECONDS = 4_294_967_296L
    private fun uint32(bytes: ByteArray, at: Int): Long = (0..3).fold(0L) { v, i -> (v shl 8) or (bytes[at + i].toLong() and 255) }
    private fun write32(bytes: ByteArray, at: Int, value: Long) { for (i in 0..3) bytes[at + i] = (value ushr (24 - i * 8)).toByte() }
    fun writeTimestamp(bytes: ByteArray, at: Int, unixNanos: Long) {
        val seconds = Math.floorDiv(unixNanos, 1_000_000_000L)
        val fraction = Math.floorMod(unixNanos, 1_000_000_000L)
        write32(bytes, at, seconds + EPOCH_SECONDS)
        write32(bytes, at + 4, fraction * ERA_SECONDS / 1_000_000_000L)
    }
    fun timestamp(bytes: ByteArray, at: Int, pivotNanos: Long): Long {
        val rawSeconds = uint32(bytes, at)
        val pivotSeconds = Math.floorDiv(pivotNanos, 1_000_000_000L) + EPOCH_SECONDS
        val era = Math.floorDiv(pivotSeconds - rawSeconds + ERA_SECONDS / 2, ERA_SECONDS)
        return (rawSeconds + era * ERA_SECONDS - EPOCH_SECONDS) * 1_000_000_000L + uint32(bytes, at + 4) * 1_000_000_000L / ERA_SECONDS
    }
    fun request(sentUtcNanos: Long): ByteArray = ByteArray(48).also {
        it[0] = 0x23 // LI=0, version=4, mode=3 (client)
        writeTimestamp(it, 40, sentUtcNanos)
    }
    fun parse(request: ByteArray, response: ByteArray, length: Int, sentUtc: Long, sentMono: Long, receivedMono: Long): NtpSample {
        require(length >= 48 && length <= response.size) { "NTP応答が短すぎます" }
        val header = response[0].toInt() and 255
        require(header ushr 6 != 3) { "NTPサーバーが未同期です" }
        require((header ushr 3 and 7) in 3..4 && (header and 7) == 4) { "NTPバージョン・モードが不正です" }
        require((response[1].toInt() and 255) in 1..15) { "NTP stratumが無効です（レート制限を含む）" }
        require((0..7).all { request[40 + it] == response[24 + it] }) { "NTP要求と応答が一致しません" }
        require((32..39).any { response[it] != 0.toByte() } && (40..47).any { response[it] != 0.toByte() }) { "NTP時刻が空です" }
        val elapsed = receivedMono - sentMono
        require(elapsed in 0..5_000_000_000L) { "応答時間が範囲外です" }
        // Derive t4 from monotonic elapsed time, so wall clock changes during the request do not matter.
        val t4 = sentUtc + elapsed
        val t2 = timestamp(response, 32, sentUtc)
        val t3 = timestamp(response, 40, sentUtc)
        require(t3 >= t2 && t3 - t2 <= elapsed + 1_000_000L) { "NTP処理時間が不正です" }
        val rtt = max(0L, elapsed - (t3 - t2))
        val offset = ((t2 - sentUtc) / 2) + ((t3 - t4) / 2)
        val rootDelay = uint32(response, 4).toInt().toLong() * 1_000_000_000L / 65_536
        val dispersion = uint32(response, 8) * 1_000_000_000L / 65_536
        require(dispersion <= 10_000_000_000L && abs(rootDelay) <= 10_000_000_000L) { "NTPルート誤差が大きすぎます" }
        val precision = 1_000_000L // Conservative local timestamp / scheduling allowance.
        return NtpSample(ClockAnchor(t4 + offset, receivedMono), rtt, offset,
            rtt / 2 + max(0L, rootDelay) / 2 + dispersion + precision)
    }
}

class NtpClient(private val monotonic: MonotonicClock, private val wallMillis: () -> Long = System::currentTimeMillis) : NtpSource {
    override suspend fun measure(server: String): List<NtpSample> = withContext(Dispatchers.IO) {
        val addresses = InetAddress.getAllByName(server)
        val samples = mutableListOf<NtpSample>()
        var lastError = "NTP応答がありません"
        repeat(5) { index ->
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = 1500
                    socket.connect(InetSocketAddress(addresses[index % addresses.size], 123))
                    val sentMono = monotonic.nanos()
                    val sentUtc = wallMillis() * 1_000_000L
                    val request = NtpPacket.request(sentUtc)
                    socket.send(DatagramPacket(request, request.size))
                    val bytes = ByteArray(512)
                    val packet = DatagramPacket(bytes, bytes.size)
                    socket.receive(packet)
                    val receivedMono = monotonic.nanos()
                    samples += NtpPacket.parse(request, bytes, packet.length, sentUtc, sentMono, receivedMono)
                }
            } catch (e: IOException) { lastError = e.localizedMessage ?: "ネットワークエラー" }
              catch (e: IllegalArgumentException) { lastError = e.message ?: "無効なNTP応答" }
            // Avoid flooding the public time server. No periodic resynchronization follows this batch.
            if (index < 4) delay(1000)
        }
        if (samples.isEmpty()) throw IOException("同期失敗: $lastError")
        samples
    }
}
