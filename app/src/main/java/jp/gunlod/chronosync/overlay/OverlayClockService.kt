package jp.gunlod.chronosync.overlay

import android.app.*
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.*
import android.widget.*
import jp.gunlod.chronosync.*
import jp.gunlod.chronosync.data.AppSettings
import jp.gunlod.chronosync.time.ClockText
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class OverlayClockService : Service() {
    companion object {
        const val STOP = "jp.gunlod.chronosync.STOP"
        private val mutableRunning = MutableStateFlow(false)
        val running = mutableRunning.asStateFlow()
    }
    private val app get() = application as ChronoSyncApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private lateinit var clockView: OverlayClockView
    private lateinit var clockParams: WindowManager.LayoutParams
    private var adjustmentPanel: View? = null
    private var offsetLabel: TextView? = null
    private val stepButtons = mutableListOf<Button>()
    private var adjustment = false
    private var attached = false
    private var receiverRegistered = false
    private var lastSettings: AppSettings? = null
    private var floatX = 0f
    private var floatY = 0f
    private val screenEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> clockView.setRendering(false)
                Intent.ACTION_SCREEN_ON -> clockView.setRendering(true)
            }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            app.settings.update { it.copy(overlayEnabled = false) }
            stopSelf(); return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            promote()
            if (!attached) startClock()
        } catch (e: RuntimeException) {
            app.settings.update { it.copy(overlayEnabled = false) }
            Toast.makeText(this, "オーバーレイを開始できません: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY // No hidden background restart or boot receiver.
    }
    private fun promote() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("clock", "時計の表示", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "clock").setSmallIcon(R.drawable.ic_clock)
            .setContentTitle("ChronoSync 表示中").setContentText("時計をタップして補正できます")
            .setContentIntent(open).setOngoing(true).setShowWhen(false)
            .addAction(Notification.Action.Builder(null, "停止", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, notification)
    }
    private fun dp(v: Int) = (resources.displayMetrics.density * v).roundToInt()
    @SuppressLint("RtlHardcoded") // Saved x/y and safeBounds use physical left/top coordinates, also in RTL.
    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        // Keep each untrusted overlay at/below Android's default maximum obscuring opacity.
        alpha = 0.8f
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
    }
    @Suppress("DEPRECATION")
    private fun safeBounds(): Rect {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = wm.currentWindowMetrics
            val inset = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Rect(inset.left, inset.top, metrics.bounds.width() - inset.right, metrics.bounds.height() - inset.bottom)
        }
        val metrics = android.util.DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        val insets = clockView.rootWindowInsets
        return Rect(insets?.systemWindowInsetLeft ?: 0, insets?.systemWindowInsetTop ?: dp(24),
            metrics.widthPixels - (insets?.systemWindowInsetRight ?: 0), metrics.heightPixels - (insets?.systemWindowInsetBottom ?: dp(48)))
    }
    private fun startClock() {
        wm = getSystemService(WindowManager::class.java)
        clockParams = params()
        clockView = OverlayClockView(this, app)
        clockView.setOnApplyWindowInsetsListener { _, insets ->
            clockView.post { if (attached) { positionClock(); positionPanels() } }
            insets
        }
        clockView.setOnClickListener { adjustment = !adjustment; rebuildPanels() }
        clockView.onDrag = { dx, dy ->
            floatX += dx; floatY += dy
            clockParams.x = floatX.roundToInt(); clockParams.y = floatY.roundToInt()
            positionClock(); positionPanels()
        }
        clockView.onDragEnd = { savePosition() }
        wm.addView(clockView, clockParams)
        attached = true
        mutableRunning.value = true
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenEvents, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(screenEvents, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) })
        receiverRegistered = true
        clockView.setRendering(getSystemService(PowerManager::class.java).isInteractive)
        scope.launch {
            app.settings.awaitLoaded()
            if (!app.settings.settings.value.overlayEnabled) app.settings.update { it.copy(overlayEnabled = true) }
            app.settings.settings.collect { settings ->
                val old = lastSettings
                lastSettings = settings
                clockView.applySettings(settings)
                if (old == null || old.x != settings.x || old.y != settings.y) {
                    clockParams.x = settings.x; clockParams.y = settings.y
                }
                positionClock()
                offsetLabel?.text = ClockText.offset(settings.offsetMs)
                stepButtons.forEach { styleStep(it, it.tag == settings.stepMs) }
                clockView.render()
                positionPanels()
            }
        }
        app.initialSync()
    }
    private fun positionClock() {
        val bounds = safeBounds()
        clockView.measure(View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.AT_MOST))
        // Fit large system font scales on narrow displays without truncating the time.
        val measuredText = clockView.paint.measureText("00:00:00.000") + clockView.paddingLeft + clockView.paddingRight
        if (measuredText > bounds.width()) {
            clockView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, clockView.textSize * (bounds.width() - dp(12)).coerceAtLeast(1) / measuredText)
            clockView.measure(View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(bounds.height(), View.MeasureSpec.AT_MOST))
        }
        clockParams.width = clockView.measuredWidth; clockParams.height = clockView.measuredHeight
        clockParams.x = clockParams.x.coerceIn(bounds.left, (bounds.right - clockParams.width).coerceAtLeast(bounds.left))
        clockParams.y = clockParams.y.coerceIn(bounds.top, (bounds.bottom - clockParams.height).coerceAtLeast(bounds.top))
        floatX = clockParams.x.toFloat(); floatY = clockParams.y.toFloat()
        if (attached) wm.updateViewLayout(clockView, clockParams)
    }
    private fun savePosition() { app.settings.update { it.copy(x = clockParams.x, y = clockParams.y) } }
    private fun button(label: String, description: String = label, action: () -> Unit): Button = Button(this).apply {
        text = label; contentDescription = description; textSize = 14f
        minWidth = dp(48); minimumWidth = dp(48); minHeight = dp(48); minimumHeight = dp(48)
        setPadding(dp(4), 0, dp(4), 0)
        setTextColor(android.graphics.Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF294A44.toInt())
        setOnClickListener { action() }
    }
    private fun styleStep(button: Button, selected: Boolean) {
        button.isSelected = selected
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selected) 0xFF267D6B.toInt() else 0xFF253B40.toInt())
        button.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFFE1EEE9.toInt())
    }
    private fun removePanels() {
        adjustmentPanel?.let { if (it.isAttachedToWindow) wm.removeViewImmediate(it) }
        adjustmentPanel = null; offsetLabel = null; stepButtons.clear()
    }
    private fun rebuildPanels() {
        removePanels()
        if (!adjustment || !attached) return
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xFF16252B.toInt()) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                addView(button("－", "補正値を減らす") { app.offsets.adjust(-1) }, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(button("＋", "補正値を増やす") { app.offsets.adjust(1) }, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                    leftMargin = dp(8)
                })
            }, LinearLayout.LayoutParams(-1, dp(48)))
            offsetLabel = TextView(context).apply { text = ClockText.offset(app.settings.settings.value.offsetMs); gravity = Gravity.CENTER; textSize = 15f; setTextColor(-1) }
            addView(offsetLabel, LinearLayout.LayoutParams(-1, dp(28)))
            addView(LinearLayout(context).apply {
                listOf(1, 10, 100).forEach { step ->
                    val b = button("${step}ms", "補正ステップ ${step}ミリ秒") { app.offsets.step(step) }.apply { tag = step; styleStep(this, step == app.settings.settings.value.stepMs) }
                    stepButtons += b
                    addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
                }
            })
            addView(LinearLayout(context).apply {
                addView(button("リセット") { app.offsets.reset() }, LinearLayout.LayoutParams(0, dp(48), 1f))
                addView(button("閉じる") { adjustment = false; rebuildPanels() }, LinearLayout.LayoutParams(0, dp(48), 1f))
            })
        }
        adjustmentPanel = ScrollView(this).apply { isFillViewport = true; addView(content) }
        wm.addView(adjustmentPanel, params())
        positionPanels()
    }
    /** Place touchable windows only in free rectangles. Clock coordinates never change on opening. */
    private fun positionPanels() {
        if (!adjustment || adjustmentPanel == null) return
        val bounds = safeBounds()
        val clock = Rect(clockParams.x, clockParams.y, clockParams.x + clockParams.width, clockParams.y + clockParams.height)
        val gap = dp(3)
        val available = listOf(
            Rect(bounds.left, bounds.top, bounds.right, (clock.top - gap).coerceAtLeast(bounds.top)),
            Rect(bounds.left, (clock.bottom + gap).coerceAtMost(bounds.bottom), bounds.right, bounds.bottom),
            Rect(bounds.left, clock.top, (clock.left - gap).coerceAtLeast(bounds.left), clock.bottom),
            Rect((clock.right + gap).coerceAtMost(bounds.right), clock.top, bounds.right, clock.bottom),
        ).filter { it.width() >= dp(120) && it.height() >= dp(48) }
        val preferred = available.firstOrNull { it.top >= clock.bottom && it.height() >= dp(172) }
            ?: available.firstOrNull { it.bottom <= clock.top && it.height() >= dp(172) }
            ?: available.maxByOrNull { it.width().coerceAtMost(dp(228)).toLong() * it.height().coerceAtMost(dp(172)) }
        if (preferred == null) { adjustment = false; removePanels(); return }
        val p = adjustmentPanel!!.layoutParams as WindowManager.LayoutParams
        p.width = dp(228).coerceAtMost(preferred.width()); p.height = dp(172).coerceAtMost(preferred.height())
        p.x = (clock.centerX() - p.width / 2).coerceIn(preferred.left, preferred.right - p.width)
        p.y = if (preferred.bottom <= clock.top) preferred.bottom - p.height else preferred.top
        wm.updateViewLayout(adjustmentPanel, p)
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (attached) { lastSettings?.let { clockView.applySettings(it) }; positionClock(); positionPanels(); savePosition() }
    }
    override fun onDestroy() {
        scope.cancel()
        if (receiverRegistered) unregisterReceiver(screenEvents)
        if (attached) {
            removePanels(); clockView.setRendering(false)
            if (clockView.isAttachedToWindow) wm.removeViewImmediate(clockView)
        }
        mutableRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
