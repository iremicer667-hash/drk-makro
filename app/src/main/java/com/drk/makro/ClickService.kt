package com.drk.makro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import kotlin.math.abs

/**
 * DRK Makro servisi:
 *  - Ekranda sürüklenebilir BAŞLAT/DURDUR butonu gösterir.
 *  - Ekranda sürüklenebilir hedef halkası gösterir (tıklanacak nokta).
 *  - Makro çalışırken halka dokunuşları engellemez, oyunda normal hareket edebilirsin.
 */
class ClickService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClickService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager

    private var controlButton: TextView? = null
    private var targetMarker: View? = null
    private lateinit var controlParams: WindowManager.LayoutParams
    private lateinit var markerParams: WindowManager.LayoutParams

    private var running = false
    private var intervalMs = 100L

    override fun onServiceConnected() {
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopClicking()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hidePanel()
        instance = null
        return super.onUnbind(intent)
    }
        private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun overlayParams(size: Int, x: Int, y: Int) = WindowManager.LayoutParams(
        size, size,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

    fun showPanel() {
        if (controlButton != null) return

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        // Hedef halkası (tıklanacak nokta)
        val markerSize = dp(56)
        markerParams = overlayParams(markerSize, screenWidth / 2, screenHeight / 2)
        val marker = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FF6A00"))
                setStroke(dp(3), Color.parseColor("#FF6A00"))
            }
        }
        makeDraggable(marker, markerParams, null)
        windowManager.addView(marker, markerParams)
        targetMarker = marker

        // Başlat / Durdur butonu
        val buttonSize = dp(72)
        controlParams = overlayParams(buttonSize, dp(16), screenHeight / 3)
        val button = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        makeDraggable(button, controlParams) { toggleClicking() }
        windowManager.addView(button, controlParams)
        controlButton = button
        updateButtonLook()
    }

    fun hidePanel() {
        stopClicking()
        controlButton?.let { runCatching { windowManager.removeView(it) } }
        targetMarker?.let { runCatching { windowManager.removeView(it) } }
        controlButton = null
        targetMarker = null
    }

    /** View'i parmakla sürüklenebilir yapar; sürüklenmeden dokunulursa onTap çalışır. */
    private fun makeDraggable(
        view: View,
        params: WindowManager.LayoutParams,
        onTap: (() -> Unit)?
    ) {
        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap?.invoke()
                    true
                }
                else -> false
            }
        }
    }
        private fun updateButtonLook() {
        val button = controlButton ?: return
        button.text = if (running) "DURDUR" else "BAŞLAT"
        button.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(if (running) "#CC2B2B" else "#1E9E4A"))
            setStroke(dp(2), Color.WHITE)
        }
    }

    /** Çalışırken halka dokunuşları geçirsin (hareketi engellemesin). */
    private fun setMarkerTouchable(touchable: Boolean) {
        val marker = targetMarker ?: return
        markerParams.flags = if (touchable) {
            markerParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            markerParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        windowManager.updateViewLayout(marker, markerParams)
    }

    private fun toggleClicking() {
        if (running) stopClicking() else startClicking()
    }

    private fun startClicking() {
        if (targetMarker == null) return
        intervalMs = Prefs.intervalMs(this)
        running = true
        setMarkerTouchable(false)
        updateButtonLook()
        handler.post(tapLoop)
    }

    private fun stopClicking() {
        running = false
        handler.removeCallbacks(tapLoop)
        setMarkerTouchable(true)
        updateButtonLook()
    }

    private val tapLoop = object : Runnable {
        override fun run() {
            if (!running) return
            val begin = SystemClock.uptimeMillis()
            sendTap {
                if (running) {
                    val elapsed = SystemClock.uptimeMillis() - begin
                    handler.postDelayed(this, (intervalMs - elapsed).coerceAtLeast(0L))
                }
            }
        }
    }

    /** Halkanın merkezine tek dokunuş gönderir; bitince onFinished çağrılır. */
    private fun sendTap(onFinished: () -> Unit) {
        val marker = targetMarker ?: return onFinished()
        val location = IntArray(2)
        marker.getLocationOnScreen(location)
        val path = Path().apply {
            moveTo(location[0] + marker.width / 2f, location[1] + marker.height / 2f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()

        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onFinished()
            override fun onCancelled(gestureDescription: GestureDescription?) = onFinished()
        }
        if (!dispatchGesture(gesture, callback, handler)) onFinished()
    }
}
