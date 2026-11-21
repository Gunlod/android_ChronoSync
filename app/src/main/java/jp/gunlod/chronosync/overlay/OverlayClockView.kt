package jp.gunlod.chronosync.overlay

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import jp.gunlod.chronosync.ChronoSyncApp
import jp.gunlod.chronosync.data.AppSettings
import jp.gunlod.chronosync.time.ClockText
import kotlin.math.abs

/** One frame callback per display vsync; the time source itself never depends on frame cadence. */
class OverlayClockView @JvmOverloads constructor(context: Context, private val app: ChronoSyncApp = context.applicationContext as ChronoSyncApp) : TextView(context), Choreographer.FrameCallback {
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var pressedAt = 0L
    private var rendering = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    init {
        typeface = Typeface.MONOSPACE
        includeFontPadding = false
        setPadding(dp(5), dp(3), dp(5), dp(3))
        text = "--:--:--.---"
        contentDescription = "時計。タップで時刻補正、ドラッグで移動"
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    fun applySettings(settings: AppSettings) {
        textSize = settings.textSizeSp
        setTextColor(settings.textColor)
        background = GradientDrawable().apply {
            cornerRadius = dp(5).toFloat()
            setColor(android.graphics.Color.argb((settings.backgroundOpacity * 255).toInt(), 10, 15, 20))
        }
    }
    fun render() {
        val now = app.clock.nowNanos()
        val next = now?.let { ClockText.time(ClockText.corrected(it, app.settings.settings.value.offsetMs)) } ?: "--:--:--.---"
        if (text.toString() != next) text = next
    }
    fun setRendering(enabled: Boolean) {
        if (enabled == rendering) return
        rendering = enabled
        Choreographer.getInstance().removeFrameCallback(this)
        if (rendering) Choreographer.getInstance().postFrameCallback(this)
    }
    override fun doFrame(frameTimeNanos: Long) {
        if (!rendering) return
        render()
        Choreographer.getInstance().postFrameCallback(this)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); setRendering(true) }
    override fun onDetachedFromWindow() { setRendering(false); super.onDetachedFromWindow() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX; startY = event.rawY; lastX = startX; lastY = startY
                pressedAt = event.eventTime; dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(event.rawX - startX) > slop || abs(event.rawY - startY) > slop)) dragging = true
                if (dragging) {
                    onDrag?.invoke(event.rawX - lastX, event.rawY - lastY)
                    lastX = event.rawX; lastY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) onDragEnd?.invoke()
                else if (event.eventTime - pressedAt < ViewConfiguration.getLongPressTimeout()) performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { if (dragging) onDragEnd?.invoke(); dragging = false; return true }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean = super.performClick()
}
