package com.autoscroller.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.core.app.NotificationCompat
import com.autoscroller.app.R
import com.autoscroller.app.data.PreferencesManager
import com.autoscroller.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FloatingControlService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var prefs: PreferencesManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager.getInstance(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        try {
            startInForeground()
            initFloatingView()
            observeScrollState()
            prefs.isFloatingWidgetEnabled = true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FloatingControlService", e)
            stopSelf()
        }
    }

    private fun startInForeground() {
        val channelId = "floating_control_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "ReelFlow Floating Controller",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ReelFlow Active")
            .setContentText("HUD controller is visible")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, notification)
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun initFloatingView() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_AutoScroller)
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.layout_floating_widget, null)
        floatingView = view

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 350
        }

        val layoutExpanded = view.findViewById<View>(R.id.layoutExpanded)
        val layoutCollapsed = view.findViewById<View>(R.id.layoutCollapsed)
        val btnToggle = view.findViewById<ImageButton>(R.id.btnToggleAutoScroll)
        val btnSkip = view.findViewById<ImageButton>(R.id.btnSkipNext)
        val btnMinimize = view.findViewById<ImageButton>(R.id.btnMinimizeWidget)
        val statusDot = view.findViewById<View>(R.id.viewStatusDot)
        val bubbleDot = view.findViewById<View>(R.id.viewBubbleDot)

        updateToggleUI(btnToggle, statusDot, bubbleDot)

        // Play/Pause button
        btnToggle.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            prefs.isAutoScrollEnabled = !prefs.isAutoScrollEnabled
            updateToggleUI(btnToggle, statusDot, bubbleDot)
        }

        // Skip to next video
        btnSkip.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            AutoScrollAccessibilityService.instance?.performSwipeUp()
        }

        // Minimize expanded pill to tiny circular bubble
        btnMinimize.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            layoutExpanded.visibility = View.GONE
            layoutCollapsed.visibility = View.VISIBLE
            windowManager.updateViewLayout(view, layoutParams)
        }

        // Expand circular bubble back to pill
        layoutCollapsed.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            layoutCollapsed.visibility = View.GONE
            layoutExpanded.visibility = View.VISIBLE
            windowManager.updateViewLayout(view, layoutParams)
        }

        // Universal Draggable Touch Handler
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var clickStartTime = 0L

        val touchListener = View.OnTouchListener { v, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = motionEvent.rawX
                    initialTouchY = motionEvent.rawY
                    isDragging = false
                    clickStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (motionEvent.rawX - initialTouchX).toInt()
                    val deltaY = (motionEvent.rawY - initialTouchY).toInt()

                    if (Math.abs(deltaX) > 12 || Math.abs(deltaY) > 12) {
                        isDragging = true
                    }

                    if (isDragging) {
                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        try {
                            windowManager.updateViewLayout(view, layoutParams)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error moving floating HUD", e)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - clickStartTime
                    if (!isDragging && duration < 250) {
                        v.performClick()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }

        layoutExpanded.setOnTouchListener(touchListener)
        layoutCollapsed.setOnTouchListener(touchListener)

        windowManager.addView(view, layoutParams)
    }

    private fun updateToggleUI(btnToggle: ImageButton, statusDot: View, bubbleDot: View) {
        val isEnabled = prefs.isAutoScrollEnabled
        btnToggle.setImageResource(if (isEnabled) R.drawable.ic_pause else R.drawable.ic_play)
        val color = if (isEnabled) Color.parseColor("#00F59B") else Color.parseColor("#FFB300")
        setDotColor(statusDot, color)
        setDotColor(bubbleDot, color)
    }

    private fun setDotColor(dotView: View, color: Int) {
        val bg = dotView.background
        if (bg is GradientDrawable) {
            bg.setColor(color)
        }
    }

    private fun observeScrollState() {
        serviceScope.launch {
            AutoScrollAccessibilityService.isScrollingNow.collect { isScrolling ->
                floatingView?.let { view ->
                    val statusDot = view.findViewById<View>(R.id.viewStatusDot)
                    val bubbleDot = view.findViewById<View>(R.id.viewBubbleDot)
                    if (isScrolling) {
                        setDotColor(statusDot, Color.parseColor("#00D2FF")) // Cyan when swiping
                        setDotColor(bubbleDot, Color.parseColor("#00D2FF"))
                    } else {
                        val isEnabled = prefs.isAutoScrollEnabled
                        val color = if (isEnabled) Color.parseColor("#00F59B") else Color.parseColor("#FFB300")
                        setDotColor(statusDot, color)
                        setDotColor(bubbleDot, color)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.isFloatingWidgetEnabled = false
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View already detached
            }
        }
    }

    companion object {
        private const val TAG = "FloatingControlService"

        fun start(context: Context) {
            val intent = Intent(context, FloatingControlService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingControlService::class.java))
        }
    }
}
