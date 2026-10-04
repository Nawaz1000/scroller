package com.autoscroller.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.autoscroller.app.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AutoScrollAccessibilityService : AccessibilityService() {

    private val detector = VideoDetector()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: PreferencesManager

    private var isScrollPending = false
    private var scheduledSwipeRunnable: Runnable? = null
    private var scheduledForVideoTotalMs: Long = 0L

    private val swipeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(TAG, "Received broadcast to swipe up!")
            performSwipeUp()
        }
    }

    private var currentPollDelay = 2000L

    // Dynamic Polling loop: 2000ms idle, 300ms when inside a target app
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (prefs.isAutoScrollEnabled && !isScrollPending && !detector.isInCooldown()) {
                val isActive = inspectActiveWindow()
                currentPollDelay = if (isActive) 300L else 2000L
            } else {
                currentPollDelay = 2000L
            }
            handler.postDelayed(this, currentPollDelay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager.getInstance(this)
        instance = this
        _isServiceRunning.value = true

        val filter = IntentFilter(ACTION_SWIPE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(swipeReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(swipeReceiver, filter)
        }

        handler.post(pollRunnable)
        Log.i(TAG, "AutoScrollAccessibilityService created and polling started.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceRunning.value = true
        Log.i(TAG, "AutoScrollAccessibilityService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!prefs.isAutoScrollEnabled) return

        val packageName = event.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            cancelScheduledSwipe()
            detector.onNewVideoStarted()
            return
        }

        if (packageName == VideoDetector.PKG_YOUTUBE || packageName == VideoDetector.PKG_INSTAGRAM) {
            inspectActiveWindow()
        }
    }

    private fun inspectActiveWindow(): Boolean {
        if (isScrollPending || detector.isInCooldown()) return false

        val rootNode = rootInActiveWindow ?: return false
        val packageName = rootNode.packageName?.toString() ?: return false

        val isTarget = when (packageName) {
            VideoDetector.PKG_YOUTUBE -> prefs.isYouTubeEnabled
            VideoDetector.PKG_INSTAGRAM -> prefs.isInstagramEnabled
            VideoDetector.PKG_FACEBOOK -> prefs.isFacebookEnabled
            else -> false
        }
        if (!isTarget) return false

        val result = detector.evaluateVideoState(
            rootNode = rootNode,
            packageName = packageName,
            fallbackTimeoutSec = prefs.fallbackTimeoutSec,
            blockedCategories = prefs.blockedCategories
        )

        when (result) {
            is VideoDetector.VideoStateResult.ShouldScrollNow -> {
                cancelScheduledSwipe()
                triggerAutoScroll(isSkip = false)
            }
            is VideoDetector.VideoStateResult.ShouldSkip -> {
                cancelScheduledSwipe()
                triggerAutoScroll(isSkip = true)
            }
            is VideoDetector.VideoStateResult.HasRemainingTime -> {
                if (scheduledForVideoTotalMs != result.totalMs && result.totalMs > 0) {
                    scheduleSwipeAt(result.remainingMs, result.totalMs)
                }
            }
            VideoDetector.VideoStateResult.None -> {
                // Keep monitoring
            }
        }
        return true
    }

    private fun scheduleSwipeAt(remainingMs: Long, totalMs: Long) {
        cancelScheduledSwipe()
        scheduledForVideoTotalMs = totalMs

        val targetDelay = (remainingMs + prefs.scrollDelayMs).coerceAtLeast(100L)
        Log.i(TAG, "Scheduling auto-swipe in ${targetDelay}ms (Video duration: ${totalMs / 1000}s)")

        scheduledSwipeRunnable = Runnable {
            if (!isScrollPending && !detector.isInCooldown()) {
                Log.i(TAG, "Scheduled auto-swipe timer fired!")
                performSwipeUp(isSkip = false)
            }
        }
        handler.postDelayed(scheduledSwipeRunnable!!, targetDelay)
    }

    private fun cancelScheduledSwipe() {
        scheduledSwipeRunnable?.let {
            handler.removeCallbacks(it)
            scheduledSwipeRunnable = null
        }
        scheduledForVideoTotalMs = 0L
    }

    private fun triggerAutoScroll(isSkip: Boolean) {
        if (isScrollPending || detector.isInCooldown()) return

        isScrollPending = true
        _isScrollingNow.value = true

        // If it's a skip, don't wait for delay. Just skip immediately.
        val delay = if (isSkip) 100L else prefs.scrollDelayMs
        Log.d(TAG, "Triggering swipe in ${delay}ms... (Skip: $isSkip)")

        handler.postDelayed({
            performSwipeUp(isSkip = isSkip)
            isScrollPending = false
            _isScrollingNow.value = false
        }, delay)
    }

    fun performSwipeUp(durationMs: Long = 250L, isSkip: Boolean = false): Boolean {
        cancelScheduledSwipe()
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        // Perfectly centered accurate swipe from 80% to 20%
        val startX = width / 2f
        val startY = height * 0.8f
        val endY = height * 0.2f

        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(swipePath, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val category = detector.markScrolled()
        Log.i(TAG, "Dispatching swipe up. Category: $category. Skip: $isSkip")

        _isScrollingNow.value = true
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Swipe completed.")
                if (!isSkip) {
                    prefs.recordAutoScroll(category)
                }
                _isScrollingNow.value = false
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Swipe cancelled.")
                _isScrollingNow.value = false
            }
        }, null)

        return dispatched
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted.")
        cancelScheduledSwipe()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceRunning.value = false
        cancelScheduledSwipe()
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(swipeReceiver) } catch (e: Exception) {}
    }

    companion object {
        private const val TAG = "AutoScrollService"
        const val ACTION_SWIPE = "com.autoscroller.app.ACTION_SWIPE"

        @Volatile
        var instance: AutoScrollAccessibilityService? = null
            private set

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

        private val _isScrollingNow = MutableStateFlow(false)
        val isScrollingNow: StateFlow<Boolean> = _isScrollingNow
    }
}
