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

    // Receiver to trigger swipe from ADB or other components
    private val swipeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.i(TAG, "Received broadcast to swipe up!")
            performSwipeUp()
        }
    }

    // Polling loop to inspect window state every 600ms even if no events fire during video playback
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (prefs.isAutoScrollEnabled && !isScrollPending && !detector.isInCooldown()) {
                inspectActiveWindow()
            }
            handler.postDelayed(this, 600L)
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

        // Handle page/scroll changes to reset detector
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            cancelScheduledSwipe()
            detector.onNewVideoStarted()
            return
        }

        if (packageName == VideoDetector.PKG_YOUTUBE || packageName == VideoDetector.PKG_INSTAGRAM) {
            inspectActiveWindow()
        }
    }

    private fun inspectActiveWindow() {
        if (isScrollPending || detector.isInCooldown()) return

        val rootNode = rootInActiveWindow ?: return
        val packageName = rootNode.packageName?.toString() ?: return

        val isTarget = when (packageName) {
            VideoDetector.PKG_YOUTUBE -> prefs.isYouTubeEnabled
            VideoDetector.PKG_INSTAGRAM -> prefs.isInstagramEnabled
            VideoDetector.PKG_FACEBOOK -> prefs.isFacebookEnabled
            else -> false
        }
        if (!isTarget) return

        val result = detector.evaluateVideoState(
            rootNode = rootNode,
            packageName = packageName,
            fallbackTimeoutSec = prefs.fallbackTimeoutSec
        )

        when (result) {
            is VideoDetector.VideoStateResult.ShouldScrollNow -> {
                cancelScheduledSwipe()
                triggerAutoScroll()
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
    }

    private fun scheduleSwipeAt(remainingMs: Long, totalMs: Long) {
        cancelScheduledSwipe()
        scheduledForVideoTotalMs = totalMs

        val targetDelay = (remainingMs + prefs.scrollDelayMs).coerceAtLeast(400L)
        Log.i(TAG, "Scheduling auto-swipe in ${targetDelay}ms (Video duration: ${totalMs / 1000}s)")

        scheduledSwipeRunnable = Runnable {
            if (!isScrollPending && !detector.isInCooldown()) {
                Log.i(TAG, "Scheduled auto-swipe timer fired!")
                performSwipeUp()
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

    private fun triggerAutoScroll() {
        if (isScrollPending || detector.isInCooldown()) return

        isScrollPending = true
        _isScrollingNow.value = true

        val delay = prefs.scrollDelayMs
        Log.d(TAG, "Video finished! Triggering swipe in ${delay}ms...")

        handler.postDelayed({
            performSwipeUp()
            isScrollPending = false
            _isScrollingNow.value = false
        }, delay)
    }

    /**
     * Executes programmatic swipe up gesture on the screen.
     */
    fun performSwipeUp(durationMs: Long = 220L): Boolean {
        cancelScheduledSwipe()
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        // Center-x, snappy flick from 74% down to 24% up
        val startX = width / 2f
        val startY = height * 0.74f
        val endY = height * 0.24f

        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(swipePath, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        detector.markScrolled()
        Log.i(TAG, "Dispatching swipe up gesture from ($startX, $startY) to ($startX, $endY)")

        _isScrollingNow.value = true
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Swipe gesture completed successfully.")
                prefs.recordAutoScroll()
                _isScrollingNow.value = false
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Swipe gesture was cancelled.")
                _isScrollingNow.value = false
            }
        }, null)

        return dispatched
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoScrollAccessibilityService interrupted.")
        cancelScheduledSwipe()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceRunning.value = false
        cancelScheduledSwipe()
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(swipeReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
        Log.i(TAG, "AutoScrollAccessibilityService destroyed.")
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
