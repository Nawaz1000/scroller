package com.autoscroller.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autoscroller.app.R
import com.autoscroller.app.data.PreferencesManager
import com.autoscroller.app.databinding.ActivityMainBinding
import com.autoscroller.app.service.AutoScrollAccessibilityService
import com.autoscroller.app.service.FloatingControlService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)

        initViews()
        startRadarAnimation()
        observeServiceState()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
        updateSessionStats()
        checkAndShowPermissionsPopup()
    }

    private fun checkAndShowPermissionsPopup() {
        val isA11yEnabled = isAccessibilityServiceEnabled()
        val isOverlayEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        
        if (!isA11yEnabled || !isOverlayEnabled) {
            androidx.appcompat.app.AlertDialog.Builder(this, R.style.Theme_AutoScroller)
                .setTitle("Permissions Required")
                .setMessage("ReelFlow requires Accessibility and Overlay permissions to automatically scroll videos.")
                .setCancelable(false)
                .setPositiveButton("Grant Accessibility") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    Toast.makeText(this, "Find 'ReelFlow Engine' and turn it ON", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Grant Overlay") { _, _ ->
                    openOverlaySettings()
                }
                .show()
        }
    }

    private fun initViews() {
        // Master Pulse Engine Switch
        binding.switchEngineActive.isChecked = prefs.isAutoScrollEnabled
        binding.switchEngineActive.setOnCheckedChangeListener { view, isChecked ->
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            prefs.isAutoScrollEnabled = isChecked
            updateEngineUI(isChecked, AutoScrollAccessibilityService.isServiceRunning.value)
        }

        // Floating Overlay Launcher Button
        binding.btnLaunchFloating.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please enable Overlay Permission first", Toast.LENGTH_SHORT).show()
                openOverlaySettings()
                return@setOnClickListener
            }

            prefs.isFloatingWidgetEnabled = !prefs.isFloatingWidgetEnabled
            if (prefs.isFloatingWidgetEnabled) {
                FloatingControlService.start(this)
                binding.btnLaunchFloating.text = getString(R.string.btn_hide_floating)
            } else {
                FloatingControlService.stop(this)
                binding.btnLaunchFloating.text = getString(R.string.btn_launch_floating)
            }
        }

        // Open Stats Activity
        binding.btnOpenStats.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            startActivity(Intent(this, StatsActivity::class.java))
        }

        // Target Platforms
        binding.switchYouTube.isChecked = prefs.isYouTubeEnabled
        binding.switchYouTube.setOnCheckedChangeListener { _, isChecked ->
            prefs.isYouTubeEnabled = isChecked
            updatePlatformBadge(binding.badgeYtStatus, isChecked)
        }
        updatePlatformBadge(binding.badgeYtStatus, prefs.isYouTubeEnabled)

        binding.switchInstagram.isChecked = prefs.isInstagramEnabled
        binding.switchInstagram.setOnCheckedChangeListener { _, isChecked ->
            prefs.isInstagramEnabled = isChecked
            updatePlatformBadge(binding.badgeIgStatus, isChecked)
        }
        updatePlatformBadge(binding.badgeIgStatus, prefs.isInstagramEnabled)

        binding.switchFacebook.isChecked = prefs.isFacebookEnabled
        binding.switchFacebook.setOnCheckedChangeListener { _, isChecked ->
            prefs.isFacebookEnabled = isChecked
            updatePlatformBadge(binding.badgeFbStatus, isChecked)
        }
        updatePlatformBadge(binding.badgeFbStatus, prefs.isFacebookEnabled)

        // Fine Tuning: Post-Video Delay Slider
        val currentDelaySec = prefs.scrollDelayMs / 1000f
        binding.sliderDelay.value = currentDelaySec.coerceIn(0f, 3f)
        binding.txtDelayValueBadge.text = "${String.format("%.1f", currentDelaySec)}s"
        binding.sliderDelay.addOnChangeListener { _, value, _ ->
            prefs.scrollDelayMs = (value * 1000).toLong()
            binding.txtDelayValueBadge.text = "${String.format("%.1f", value)}s"
        }

        // Fine Tuning: Safety Timeout Fallback Slider
        val currentTimeout = prefs.fallbackTimeoutSec.toFloat()
        binding.sliderTimeout.value = currentTimeout.coerceIn(10f, 120f)
        binding.txtTimeoutValueBadge.text = "${currentTimeout.toInt()}s"
        binding.sliderTimeout.addOnChangeListener { _, value, _ ->
            prefs.fallbackTimeoutSec = value.toInt()
            binding.txtTimeoutValueBadge.text = "${value.toInt()}s"
        }

        // Sleep Timer Action
        binding.btnSleepTimer.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(this, "Sleep timer: Auto-scroller will run continuously until paused.", Toast.LENGTH_SHORT).show()
        }

        // Settings Action
        binding.btnSettings.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Toast.makeText(this, "ReelFlow v1.0.0 — Premium Edition", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startRadarAnimation() {
        val rotate = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 16000L
            repeatCount = Animation.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }
        binding.imgRadarVisual.startAnimation(rotate)
    }

    private fun updatePermissionStates() {
        val isA11yEnabled = isAccessibilityServiceEnabled()
        binding.badgePermAccessibility.text = if (isA11yEnabled) getString(R.string.status_granted) else getString(R.string.status_enable)
        val a11yColor = if (isA11yEnabled) Color.parseColor("#00F59B") else Color.parseColor("#FF4D4D")
        binding.badgePermAccessibility.setTextColor(a11yColor)

        val isOverlayEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        binding.switchPermOverlay.isChecked = isOverlayEnabled

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryIgnored = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                powerManager.isIgnoringBatteryOptimizations(packageName)
        binding.badgePermBattery.text = if (isBatteryIgnored) getString(R.string.status_granted) else getString(R.string.status_enable)

        updateEngineUI(prefs.isAutoScrollEnabled, isA11yEnabled)
    }

    private fun updateEngineUI(isEngineEnabled: Boolean, isServiceRunning: Boolean) {
        if (!isServiceRunning || !isEngineEnabled) {
            binding.txtPulseSubtitle.text = getString(R.string.pulse_engine_subtitle_inactive)
            binding.badgeOnline.text = getString(R.string.badge_paused)
            binding.badgeOnline.setTextColor(Color.parseColor("#FFB300"))
            val bg = binding.badgeOnline.background
            if (bg is GradientDrawable) {
                bg.setStroke(3, Color.parseColor("#FFB300"))
            }
        } else {
            binding.txtPulseSubtitle.text = getString(R.string.pulse_engine_subtitle_active)
            binding.badgeOnline.text = getString(R.string.badge_online)
            binding.badgeOnline.setTextColor(Color.parseColor("#00F59B"))
            val bg = binding.badgeOnline.background
            if (bg is GradientDrawable) {
                bg.setStroke(3, Color.parseColor("#00F59B"))
            }
        }
    }

    private fun updateSessionStats() {
        val scrolled = prefs.videosScrolledToday
        val timeSaved = prefs.timeSavedMinutes
        binding.badgeSessionStats.text = "Videos Scrolled: $scrolled | Time Saved: ${timeSaved}m"
    }

    // Moved to StatsActivity

    private fun updatePlatformBadge(badge: android.widget.TextView, isEnabled: Boolean) {
        badge.text = if (isEnabled) getString(R.string.status_on) else getString(R.string.status_off)
        val color = if (isEnabled) Color.parseColor("#00F59B") else Color.parseColor("#555D71")
        badge.setTextColor(color)
    }

    private fun observeServiceState() {
        lifecycleScope.launch {
            AutoScrollAccessibilityService.isServiceRunning.collect { isRunning ->
                updateEngineUI(prefs.isAutoScrollEnabled, isRunning)
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                return true
            }
        }
        return false
    }
}
