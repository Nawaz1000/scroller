package com.autoscroller.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isAutoScrollEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCROLL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCROLL_ENABLED, value).apply()

    var isYouTubeEnabled: Boolean
        get() = prefs.getBoolean(KEY_YOUTUBE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_YOUTUBE_ENABLED, value).apply()

    var isInstagramEnabled: Boolean
        get() = prefs.getBoolean(KEY_INSTAGRAM_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_INSTAGRAM_ENABLED, value).apply()

    var isFacebookEnabled: Boolean
        get() = prefs.getBoolean(KEY_FACEBOOK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FACEBOOK_ENABLED, value).apply()

    var scrollDelayMs: Long
        get() = prefs.getLong(KEY_SCROLL_DELAY_MS, 500L)
        set(value) = prefs.edit().putLong(KEY_SCROLL_DELAY_MS, value).apply()

    var fallbackTimeoutSec: Int
        get() = prefs.getInt(KEY_FALLBACK_TIMEOUT_SEC, 15)
        set(value) = prefs.edit().putInt(KEY_FALLBACK_TIMEOUT_SEC, value).apply()

    var isFloatingWidgetEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_WIDGET_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_WIDGET_ENABLED, value).apply()

    // Tracking
    private fun getCurrentDateStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun getCurrentMonthStr(): String {
        return SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    }

    private fun checkAndResetDailyStats() {
        val today = getCurrentDateStr()
        val savedDate = prefs.getString(KEY_LAST_ACTIVE_DATE, "")
        if (today != savedDate) {
            prefs.edit()
                .putString(KEY_LAST_ACTIVE_DATE, today)
                .putInt(KEY_VIDEOS_SCROLLED_TODAY, 0)
                .apply()
        }

        val thisMonth = getCurrentMonthStr()
        val savedMonth = prefs.getString(KEY_LAST_ACTIVE_MONTH, "")
        if (thisMonth != savedMonth) {
            prefs.edit()
                .putString(KEY_LAST_ACTIVE_MONTH, thisMonth)
                .putInt(KEY_VIDEOS_SCROLLED_MONTH, 0)
                .apply()
        }
    }

    var videosScrolledToday: Int
        get() {
            checkAndResetDailyStats()
            return prefs.getInt(KEY_VIDEOS_SCROLLED_TODAY, 0)
        }
        private set(value) = prefs.edit().putInt(KEY_VIDEOS_SCROLLED_TODAY, value).apply()

    var videosScrolledMonth: Int
        get() {
            checkAndResetDailyStats()
            return prefs.getInt(KEY_VIDEOS_SCROLLED_MONTH, 0)
        }
        private set(value) = prefs.edit().putInt(KEY_VIDEOS_SCROLLED_MONTH, value).apply()

    var lifetimeScrolled: Int
        get() = prefs.getInt(KEY_LIFETIME_SCROLLED, 0)
        private set(value) = prefs.edit().putInt(KEY_LIFETIME_SCROLLED, value).apply()

    var categoryStats: Map<String, Int>
        get() {
            val jsonStr = prefs.getString(KEY_CATEGORY_STATS, "{}") ?: "{}"
            val map = mutableMapOf<String, Int>()
            try {
                val json = JSONObject(jsonStr)
                for (key in json.keys()) {
                    map[key] = json.getInt(key)
                }
            } catch (e: Exception) {}
            return map
        }
        private set(value) {
            val json = JSONObject(value).toString()
            prefs.edit().putString(KEY_CATEGORY_STATS, json).apply()
        }

    var blockedCategories: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_CATEGORIES, setOf()) ?: setOf()
        set(value) = prefs.edit().putStringSet(KEY_BLOCKED_CATEGORIES, value).apply()

    fun recordAutoScroll(category: String = "General") {
        checkAndResetDailyStats()
        videosScrolledToday = videosScrolledToday + 1
        videosScrolledMonth = videosScrolledMonth + 1
        lifetimeScrolled = lifetimeScrolled + 1

        val currentStats = categoryStats.toMutableMap()
        currentStats[category] = (currentStats[category] ?: 0) + 1
        categoryStats = currentStats
    }

    val timeSavedMinutes: Int
        get() = (videosScrolledToday * 20) / 60

    fun getBrainrotRank(): String {
        val count = videosScrolledToday
        return when {
            count <= 10 -> "The Aura Farmer \uD83D\uDDFF\u2728"
            count <= 50 -> "Sigma \uD83D\uDC3A"
            count <= 150 -> "NPC \uD83E\uDDCD"
            count <= 300 -> "Cooked \uD83D\uDC80"
            else -> "Brainrotted \uD83E\uDDDF\u200D\u2642\uFE0F"
        }
    }

    fun getBrainrotRankDesc(): String {
        val count = videosScrolledToday
        return when {
            count <= 10 -> "You're touching grass and securing the W."
            count <= 50 -> "Balanced scrolling. Very sigma."
            count <= 150 -> "Average scroller. Wake up Neo."
            count <= 300 -> "You're cooked. Close the app bro."
            else -> "Terminal doom scrolling detected."
        }
    }

    companion object {
        private const val PREFS_NAME = "auto_scroller_prefs"
        private const val KEY_AUTO_SCROLL_ENABLED = "auto_scroll_enabled"
        private const val KEY_YOUTUBE_ENABLED = "youtube_enabled"
        private const val KEY_INSTAGRAM_ENABLED = "instagram_enabled"
        private const val KEY_FACEBOOK_ENABLED = "facebook_enabled"
        private const val KEY_SCROLL_DELAY_MS = "scroll_delay_ms"
        private const val KEY_FALLBACK_TIMEOUT_SEC = "fallback_timeout_sec"
        private const val KEY_FLOATING_WIDGET_ENABLED = "floating_widget_enabled"
        
        private const val KEY_LAST_ACTIVE_DATE = "last_active_date"
        private const val KEY_LAST_ACTIVE_MONTH = "last_active_month"
        private const val KEY_VIDEOS_SCROLLED_TODAY = "videos_scrolled_today"
        private const val KEY_VIDEOS_SCROLLED_MONTH = "videos_scrolled_month"
        private const val KEY_LIFETIME_SCROLLED = "lifetime_scrolled"
        private const val KEY_CATEGORY_STATS = "category_stats"
        private const val KEY_BLOCKED_CATEGORIES = "blocked_categories"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
