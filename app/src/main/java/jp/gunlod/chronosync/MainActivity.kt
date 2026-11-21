package jp.gunlod.chronosync

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.core.net.toUri
import jp.gunlod.chronosync.overlay.OverlayClockService
import jp.gunlod.chronosync.time.*
import java.util.Locale
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var pendingEnable = false
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startOverlay() }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pendingEnable && Settings.canDrawOverlays(this)) enableOverlay()
        pendingEnable = false
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            vm.app.settings.awaitLoaded()
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (vm.app.settings.settings.value.overlayEnabled && !OverlayClockService.running.value && Settings.canDrawOverlays(this@MainActivity)) {
                    startOverlay()
                }
                awaitCancellation()
            }
        }
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) darkColorScheme(primary = Color(0xFF75E3CE), primaryContainer = Color(0xFF123C38), onPrimaryContainer = Color(0xFFB7F7E8), secondaryContainer = Color(0xFF294A44), background = Color(0xFF101418), surface = Color(0xFF172026))
                else lightColorScheme(primary = Color(0xFF006B5C), primaryContainer = Color(0xFFB9F3E5), onPrimaryContainer = Color(0xFF00201B), secondaryContainer = Color(0xFFCBE9E0), background = Color(0xFFF2F7F6), surface = Color.White)
            MaterialTheme(colorScheme = colors) {
                val state by vm.ui.collectAsStateWithLifecycle()
                SettingsScreen(vm, state, ::toggleOverlay, ::openOverlaySettings)
            }
        }
    }
    private fun openOverlaySettings() {
        overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
    }
    private fun toggleOverlay(enabled: Boolean) {
        if (!enabled) {
            vm.update { it.copy(overlayEnabled = false) }
            stopService(Intent(this, OverlayClockService::class.java)); return
        }
        if (!Settings.canDrawOverlays(this)) { pendingEnable = true; openOverlaySettings() }
        else enableOverlay()
    }
    private fun enableOverlay() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startOverlay()
    }
    private fun startOverlay() {
        // Invoked only from the visible activity after a user action / permission result.
        if (!Settings.canDrawOverlays(this)) return
        try { startForegroundService(Intent(this, OverlayClockService::class.java)) }
        catch (e: RuntimeException) {
            vm.update { it.copy(overlayEnabled = false) }
            Toast.makeText(this, "時計を開始できません: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable private fun SettingsScreen(vm: MainViewModel, state: MainUiState, toggle: (Boolean) -> Unit, permission: () -> Unit) {
    val s = state.settings
    var showPrivacy by remember { mutableStateOf(false) }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("プライバシーポリシー") },
            text = { Text(stringResource(R.string.privacy_policy_text), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("閉じる") } },
        )
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("ChronoSync", fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("NTP同期 · ミリ秒オーバーレイ時計", style = MaterialTheme.typography.bodySmall) }
                Text("JST", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            ClockPreview(vm.app, s.offsetMs)
            state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!state.ready) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("設定を読み込み中…") }
            SettingsCard("時刻同期") {
                val status = state.sync.status(state.monotonicNow)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(status.name, fontWeight = FontWeight.Bold, color = if (status == SyncStatus.SYNCED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text(if (state.sync.syncing) "測定中（最大5回）…" else "起動時・手動で同期", style = MaterialTheme.typography.bodySmall)
                }
                InfoRow("同期先", state.sync.server)
                val sample = state.sync.sample
                InfoRow("最終同期（JST）", sample?.let { ClockText.date(it.anchor.utcNanos) } ?: "未同期")
                InfoRow("RTT", sample?.let { ms(it.rttNanos) } ?: "—")
                InfoRow("端末との推定差", sample?.let { ms(it.offsetNanos) } ?: "—")
                InfoRow("同期後の経過", if (sample == null) "—" else "${state.sync.ageNanos(state.monotonicNow) / 1_000_000_000} 秒")
                InfoRow("概算誤差", state.sync.uncertaintyNanos(state.monotonicNow)?.let { "± ${ms(it)}" } ?: "—")
                state.sync.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                var server by remember(s.server) { mutableStateOf(s.server) }
                val valid = server.trim().matches(Regex("[A-Za-z0-9:.\\-]{1,253}"))
                OutlinedTextField(server, { server = it }, label = { Text("NTPサーバー") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    isError = !valid, supportingText = { Text("ホスト名またはIPアドレス（UDP 123）") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.update { it.copy(server = server.trim()) } }, enabled = state.ready && valid && !state.sync.syncing) { Text("保存") }
                    Button(onClick = { vm.update { it.copy(server = server.trim()) }; vm.app.resync() }, enabled = state.ready && valid && !state.sync.syncing) { Text("再同期") }
                }
            }
            SettingsCard("オーバーレイ時計") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("他のアプリの上に表示"); Text("タップで補正 · ドラッグで移動", style = MaterialTheme.typography.bodySmall) }
                    Switch(checked = state.running, onCheckedChange = toggle, enabled = state.ready)
                }
                TextButton(onClick = permission) { Text("オーバーレイ表示権限を設定") }
                Text("文字サイズ ${s.textSizeSp.toInt()} sp")
                Slider(value = s.textSizeSp, onValueChange = { v -> vm.update { it.copy(textSizeSp = v) } }, valueRange = 12f..36f, steps = 23, enabled = state.ready)
                Text("文字色")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("白" to 0xFFFFFFFF, "緑" to 0xFF86FFD8, "黄" to 0xFFFFE082, "青" to 0xFF90CAF9).forEach { (name, color) ->
                        FilterChip(selected = s.textColor == color.toInt(), onClick = { vm.update { it.copy(textColor = color.toInt()) } }, enabled = state.ready, label = { Text(name) })
                    }
                }
                Text("背景の不透明度 ${(s.backgroundOpacity * 100).toInt()}%")
                Slider(s.backgroundOpacity, { v -> vm.update { it.copy(backgroundOpacity = v) } }, valueRange = 0.1f..1f, enabled = state.ready)
                OutlinedButton(onClick = { vm.update { it.copy(x = 24, y = 160) } }, enabled = state.ready) { Text("表示位置をリセット") }
                Text("未同期の時計は --:--:--.--- と表示します。", style = MaterialTheme.typography.bodySmall)
            }
            SettingsCard("表示時刻の補正") {
                Text(ClockText.offset(s.offsetMs), fontSize = 28.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                var input by remember(s.offsetMs) { mutableStateOf(s.offsetMs.toString()) }
                val number = input.toIntOrNull()
                val valid = number != null && number in -9999..9999
                OutlinedTextField(input, { input = it }, singleLine = true, label = { Text("補正値（ms）") },
                    supportingText = { Text("−9999 ～ +9999 ms · 表示時刻だけに適用") }, isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                Button(onClick = { number?.let { vm.app.offsets.set(it) } }, enabled = state.ready && valid) { Text("補正値を適用") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 10, 100).forEach { step -> FilterChip(selected = s.stepMs == step, onClick = { vm.app.offsets.step(step) }, enabled = state.ready, label = { Text("$step ms") }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.app.offsets.adjust(-1) }, enabled = state.ready) { Text("− ${s.stepMs} ms") }
                    OutlinedButton(onClick = { vm.app.offsets.adjust(1) }, enabled = state.ready) { Text("＋ ${s.stepMs} ms") }
                    TextButton(onClick = { vm.app.offsets.reset() }, enabled = state.ready) { Text("リセット") }
                }
            }
            SettingsCard("アプリ情報") {
                Text("ChronoSync ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                Text("開発者：Gunlod")
                Text("サポート：gunlod.0122+playstore@gmail.com", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showPrivacy = true }) { Text("プライバシーポリシー") }
                Text("NTPの往復通信からUTC時刻を推定し、同期後はスリープ時間を含む単調増加時計で時刻を進めます。端末の日付変更は同期済み時計に影響しません。")
                Text("ミリ秒の表示桁数は、1msの同期精度を保証しません。通信経路の非対称、端末の発振器、描画・画面の遅延で誤差が生じます。概算誤差はRTT等と20ppmの仮定に基づく目安です。1時間経過または概算誤差100ms超でDEGRADEDと表示します。")
                Text("自動再同期は行いません。再同期失敗時は以前の同期時計をDEGRADEDとして維持します。プロセス終了後は新しく同期してください。")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun ms(nanos: Long): String = String.format(Locale.US, "%.3f ms", nanos / 1_000_000.0)
@Composable private fun InfoRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
@Composable private fun ClockPreview(app: ChronoSyncApp, offset: Int) {
    var time by remember { mutableStateOf("--:--:--.---") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(app, offset, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) withFrameNanos {
                time = app.clock.nowNanos()?.let { ClockText.time(ClockText.corrected(it, offset)) } ?: "--:--:--.---"
            }
        }
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large).padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("表示時刻 · Asia/Tokyo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(time, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text("補正 ${ClockText.offset(offset)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
