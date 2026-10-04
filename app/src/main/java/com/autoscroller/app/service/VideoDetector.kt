package com.autoscroller.app.service

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class VideoDetector {

    private var lastProgressFraction: Float = 0f
    private var videoStartTime: Long = 0L
    private var lastScrollTime: Long = 0L
    private var lastDetectedTotalMs: Long = 0L
    private var currentCategory: String = "General"

    companion object {
        private const val TAG = "VideoDetector"
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_FACEBOOK = "com.facebook.katana"
        private const val SCROLL_COOLDOWN_MS = 2500L
        private const val FINISH_THRESHOLD = 0.98f
        private const val LOOP_HIGH_THRESHOLD = 0.85f
        private const val LOOP_LOW_THRESHOLD = 0.10f

        // Regex for YouTube Shorts contentDescription
        private val YOUTUBE_WORDS_REGEX = Regex(
            """(?:(\d+)\s*min(?:ute)?s?\s*)?(\d+)\s*sec(?:ond)?s?\s*of\s*(?:(\d+)\s*min(?:ute)?s?\s*)?(\d+)\s*sec(?:ond)?s?""",
            RegexOption.IGNORE_CASE
        )
        private val DIGITAL_REGEX = Regex(
            """(\d+):(\d+)\s*(?:of|/)\s*(\d+):(\d+)""",
            RegexOption.IGNORE_CASE
        )
        
        // Hashtag extraction
        private val HASHTAG_REGEX = Regex("""#(\w+)""")
    }

    private var categoryScanned = false

    fun onNewVideoStarted() {
        lastProgressFraction = 0f
        videoStartTime = SystemClock.uptimeMillis()
        lastDetectedTotalMs = 0L
        currentCategory = "General"
        categoryScanned = false
        Log.d(TAG, "New video detected or scroll reset.")
    }

    fun markScrolled(): String {
        lastScrollTime = SystemClock.uptimeMillis()
        val cat = currentCategory
        onNewVideoStarted()
        return cat
    }

    fun isInCooldown(): Boolean {
        return (SystemClock.uptimeMillis() - lastScrollTime) < SCROLL_COOLDOWN_MS
    }

    fun evaluateVideoState(
        rootNode: AccessibilityNodeInfo?,
        packageName: String,
        fallbackTimeoutSec: Int,
        blockedCategories: Set<String>
    ): VideoStateResult {
        if (isInCooldown()) return VideoStateResult.None
        if (rootNode == null) return VideoStateResult.None

        val now = SystemClock.uptimeMillis()

        // 0. Extract Context and Categories (ONLY ONCE PER VIDEO for BATTERY SAVINGS)
        if (!categoryScanned) {
            val onScreenText = extractOnScreenText(rootNode)
            if (onScreenText.isNotBlank()) {
                currentCategory = categorizeText(onScreenText)
                categoryScanned = true
                Log.d(TAG, "Category scanned once: $currentCategory")
                
                if (blockedCategories.isNotEmpty() && blockedCategories.contains(currentCategory)) {
                    Log.i(TAG, "Blocked category detected: $currentCategory. Triggering skip.")
                    return VideoStateResult.ShouldSkip
                }
            }
        }

        // 1. Check YouTube Shorts exact seek bar time
        if (packageName == PKG_YOUTUBE) {
            val timing = findYouTubeTiming(rootNode)
            if (timing != null) {
                val (currentMs, totalMs) = timing
                lastDetectedTotalMs = totalMs
                val remainingMs = (totalMs - currentMs).coerceAtLeast(0L)

                Log.d(TAG, "YouTube Shorts timing: ${currentMs / 1000}s of ${totalMs / 1000}s (remaining: ${remainingMs / 1000}s)")

                // If user seeks manually to the end, remainingMs becomes very small. 
                // We expand the threshold slightly to catch manual seeks to the very end.
                if (totalMs > 0 && (currentMs >= (totalMs - 1000L) || remainingMs < 1000L)) {
                    Log.i(TAG, "YouTube Short reached end (${currentMs}ms / ${totalMs}ms). Triggering scroll.")
                    return VideoStateResult.ShouldScrollNow
                }

                return VideoStateResult.HasRemainingTime(remainingMs, totalMs)
            }
        }

        // 2. Check general ProgressBar / RangeInfo
        val progress = findProgressBarFraction(rootNode, packageName)
        if (progress != null) {
            val prev = lastProgressFraction
            lastProgressFraction = progress

            Log.d(TAG, "[$packageName] Progress: ${(progress * 100).toInt()}% (prev: ${(prev * 100).toInt()}%)")

            if (progress >= FINISH_THRESHOLD) {
                Log.i(TAG, "Video progress >= 98% ($progress). Triggering scroll.")
                return VideoStateResult.ShouldScrollNow
            }

            // Loop detection: was near end and looped back to start (Catch natural loop, ignore manual scrub back)
            if (prev >= LOOP_HIGH_THRESHOLD && progress <= LOOP_LOW_THRESHOLD) {
                // If the jump is too sudden, it might be a user scrub. But for looping, this is exactly what happens.
                Log.i(TAG, "Video loop detected ($prev -> $progress). Triggering scroll.")
                return VideoStateResult.ShouldScrollNow
            }
        }

        // 3. Fallback timeout
        if (videoStartTime > 0 && (now - videoStartTime) >= (fallbackTimeoutSec * 1000L)) {
            Log.i(TAG, "Video exceeded fallback timeout ($fallbackTimeoutSec s). Triggering scroll.")
            return VideoStateResult.ShouldScrollNow
        }

        return VideoStateResult.None
    }

    private fun extractOnScreenText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = java.lang.StringBuilder()
        
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            sb.append(extractOnScreenText(child))
            child?.recycle()
        }
        return sb.toString()
    }

    private fun categorizeText(text: String): String {
        val lowerText = text.lowercase()
        val tags = HASHTAG_REGEX.findAll(lowerText).map { it.groupValues[1] }.toList()
        
        val gamingKeywords = setOf("gaming", "gta", "minecraft", "roblox", "fortnite", "valorant", "gameplay")
        val techKeywords = setOf("tech", "programming", "coding", "developer", "ai", "software")
        val comedyKeywords = setOf("funny", "meme", "comedy", "joke", "prank", "humor")
        val sportsKeywords = setOf("sports", "football", "soccer", "basketball", "nba", "nfl", "cricket")

        for (tag in tags) {
            if (tag in gamingKeywords) return "Gaming"
            if (tag in techKeywords) return "Tech"
            if (tag in comedyKeywords) return "Comedy"
            if (tag in sportsKeywords) return "Sports"
        }
        
        for (word in lowerText.split(Regex("\\s+"))) {
            if (word in gamingKeywords) return "Gaming"
            if (word in techKeywords) return "Tech"
            if (word in comedyKeywords) return "Comedy"
            if (word in sportsKeywords) return "Sports"
        }

        return "General"
    }

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
        object ShouldSkip : VideoStateResult()
        data class HasRemainingTime(val remainingMs: Long, val totalMs: Long) : VideoStateResult()
    }
}
