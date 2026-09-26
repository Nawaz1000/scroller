package com.autoscroller.app.service

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class VideoDetector {

    private var lastProgressFraction: Float = 0f
    private var videoStartTime: Long = 0L
    private var lastScrollTime: Long = 0L
    private var lastDetectedTotalMs: Long = 0L

    companion object {
        private const val TAG = "VideoDetector"
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_FACEBOOK = "com.facebook.katana"
        private const val SCROLL_COOLDOWN_MS = 1800L
        private const val FINISH_THRESHOLD = 0.97f
        private const val LOOP_HIGH_THRESHOLD = 0.80f
        private const val LOOP_LOW_THRESHOLD = 0.12f

        // Regex for YouTube Shorts contentDescription (e.g. "0 minutes 5 seconds of 0 minutes 33 seconds")
        private val YOUTUBE_WORDS_REGEX = Regex(
            """(?:(\d+)\s*min(?:ute)?s?\s*)?(\d+)\s*sec(?:ond)?s?\s*of\s*(?:(\d+)\s*min(?:ute)?s?\s*)?(\d+)\s*sec(?:ond)?s?""",
            RegexOption.IGNORE_CASE
        )
        // Regex for digital format (e.g. "0:05 of 0:33" or "0:05 / 0:33")
        private val DIGITAL_REGEX = Regex(
            """(\d+):(\d+)\s*(?:of|/)\s*(\d+):(\d+)""",
            RegexOption.IGNORE_CASE
        )
    }

    fun onNewVideoStarted() {
        lastProgressFraction = 0f
        videoStartTime = SystemClock.uptimeMillis()
        lastDetectedTotalMs = 0L
        Log.d(TAG, "New video detected or scroll reset.")
    }

    fun markScrolled() {
        lastScrollTime = SystemClock.uptimeMillis()
        onNewVideoStarted()
    }

    fun isInCooldown(): Boolean {
        return (SystemClock.uptimeMillis() - lastScrollTime) < SCROLL_COOLDOWN_MS
    }

    /**
     * Checks if video has finished or extracts video timing information.
     * Returns true if video should scroll immediately, or returns remaining duration if known.
     */
    fun evaluateVideoState(
        rootNode: AccessibilityNodeInfo?,
        packageName: String,
        fallbackTimeoutSec: Int
    ): VideoStateResult {
        if (isInCooldown()) return VideoStateResult.None
        if (rootNode == null) return VideoStateResult.None

        val now = SystemClock.uptimeMillis()

        // 1. Check YouTube Shorts exact seek bar time
        if (packageName == PKG_YOUTUBE) {
            val timing = findYouTubeTiming(rootNode)
            if (timing != null) {
                val (currentMs, totalMs) = timing
                lastDetectedTotalMs = totalMs
                val remainingMs = (totalMs - currentMs).coerceAtLeast(0L)

                Log.d(TAG, "YouTube Shorts timing: ${currentMs / 1000}s of ${totalMs / 1000}s (remaining: ${remainingMs / 1000}s)")

                // Video completed condition
                if (totalMs > 0 && currentMs >= (totalMs - 1200L)) {
                    Log.i(TAG, "YouTube Short reached end (${currentMs}ms / ${totalMs}ms). Triggering scroll.")
                    return VideoStateResult.ShouldScrollNow
                }

                // If starting or playing, report remaining time
                return VideoStateResult.HasRemainingTime(remainingMs, totalMs)
            }
        }

        // 2. Check general ProgressBar / RangeInfo (works for Instagram & YouTube)
        val progress = findProgressBarFraction(rootNode, packageName)
        if (progress != null) {
            val prev = lastProgressFraction
            lastProgressFraction = progress

            Log.d(TAG, "[$packageName] Progress: ${(progress * 100).toInt()}% (prev: ${(prev * 100).toInt()}%)")

            if (progress >= FINISH_THRESHOLD) {
                Log.i(TAG, "Video progress >= 97% ($progress). Triggering scroll.")
                return VideoStateResult.ShouldScrollNow
            }

            // Loop detection: was near end and looped back to start
            if (prev >= LOOP_HIGH_THRESHOLD && progress <= LOOP_LOW_THRESHOLD) {
                Log.i(TAG, "Video loop detected ($prev -> $progress). Triggering scroll.")
                return VideoStateResult.ShouldScrollNow
            }
        }

        // 3. Check fallback timeout
        if (videoStartTime > 0 && (now - videoStartTime) >= (fallbackTimeoutSec * 1000L)) {
            Log.i(TAG, "Video exceeded fallback timeout ($fallbackTimeoutSec s). Triggering scroll.")
            return VideoStateResult.ShouldScrollNow
        }

        return VideoStateResult.None
    }

    /**
     * Traverses the node tree looking for YouTube's seek bar with time description.
     */
    private fun findYouTubeTiming(node: AccessibilityNodeInfo?): Pair<Long, Long>? {
        if (node == null) return null

        try {
            val desc = node.contentDescription?.toString()
            if (!desc.isNullOrEmpty()) {
                val parsed = parseTimeDescription(desc)
                if (parsed != null) return parsed
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i)
                val timing = findYouTubeTiming(child)
                child?.recycle()
                if (timing != null) return timing
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding YouTube timing", e)
        }

        return null
    }

    private fun parseTimeDescription(desc: String): Pair<Long, Long>? {
        // Match word format: "0 minutes 5 seconds of 0 minutes 33 seconds"
        YOUTUBE_WORDS_REGEX.find(desc)?.let { match ->
            val curMin = match.groups[1]?.value?.toLongOrNull() ?: 0L
            val curSec = match.groups[2]?.value?.toLongOrNull() ?: 0L
            val totMin = match.groups[3]?.value?.toLongOrNull() ?: 0L
            val totSec = match.groups[4]?.value?.toLongOrNull() ?: 0L

            val currentMs = ((curMin * 60) + curSec) * 1000L
            val totalMs = ((totMin * 60) + totSec) * 1000L
            if (totalMs > 0) {
                return Pair(currentMs, totalMs)
            }
        }

        // Match digital format: "0:05 of 0:33"
        DIGITAL_REGEX.find(desc)?.let { match ->
            val curMin = match.groups[1]?.value?.toLongOrNull() ?: 0L
            val curSec = match.groups[2]?.value?.toLongOrNull() ?: 0L
            val totMin = match.groups[3]?.value?.toLongOrNull() ?: 0L
            val totSec = match.groups[4]?.value?.toLongOrNull() ?: 0L

            val currentMs = ((curMin * 60) + curSec) * 1000L
            val totalMs = ((totMin * 60) + totSec) * 1000L
            if (totalMs > 0) {
                return Pair(currentMs, totalMs)
            }
        }

        return null
    }

    private fun findProgressBarFraction(node: AccessibilityNodeInfo?, packageName: String): Float? {
        if (node == null) return null

        try {
            node.rangeInfo?.let { range ->
                if (range.max > range.min && range.current >= range.min) {
                    val fraction = (range.current - range.min) / (range.max - range.min)
                    return fraction.coerceIn(0f, 1f)
                }
            }

            val className = node.className?.toString() ?: ""
            if (className.contains("ProgressBar", ignoreCase = true) ||
                className.contains("SeekBar", ignoreCase = true)
            ) {
                node.rangeInfo?.let { range ->
                    val fraction = (range.current - range.min) / (range.max - range.min)
                    return fraction.coerceIn(0f, 1f)
                }
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i)
                val childFraction = findProgressBarFraction(child, packageName)
                child?.recycle()
                if (childFraction != null) return childFraction
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting node hierarchy", e)
        }

        return null
    }

    sealed class VideoStateResult {
        object None : VideoStateResult()
        object ShouldScrollNow : VideoStateResult()
        data class HasRemainingTime(val remainingMs: Long, val totalMs: Long) : VideoStateResult()
    }
}
