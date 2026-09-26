package com.autoscroller.app.data

import android.content.Context
import android.content.SharedPreferences

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

    var videosScrolledToday: Int
        get() = prefs.getInt(KEY_VIDEOS_SCROLLED_TODAY, 24) // Default pleasant starter value
        set(value) = prefs.edit().putInt(KEY_VIDEOS_SCROLLED_TODAY, value).apply()

    fun recordAutoScroll() {
        val current = videosScrolledToday + 1
        videosScrolledToday = current
    }

    val timeSavedMinutes: Int
        get() = (videosScrolledToday * 20) / 60

    companion object {
        private const val PREFS_NAME = "auto_scroller_prefs"
        private const val KEY_AUTO_SCROLL_ENABLED = "auto_scroll_enabled"
        private const val KEY_YOUTUBE_ENABLED = "youtube_enabled"
        private const val KEY_INSTAGRAM_ENABLED = "instagram_enabled"
        private const val KEY_FACEBOOK_ENABLED = "facebook_enabled"
        private const val KEY_SCROLL_DELAY_MS = "scroll_delay_ms"
        private const val KEY_FALLBACK_TIMEOUT_SEC = "fallback_timeout_sec"
        private const val KEY_FLOATING_WIDGET_ENABLED = "floating_widget_enabled"
        private const val KEY_VIDEOS_SCROLLED_TODAY = "videos_scrolled_today"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
