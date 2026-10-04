package com.autoscroller.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.autoscroller.app.R
import com.autoscroller.app.data.PreferencesManager
import com.autoscroller.app.databinding.ActivityStatsBinding

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)

        updateStats()

        binding.btnShareRank.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            shareRank()
        }

        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            binding.btnLogin.text = "Logged in as ${auth.currentUser?.email?.substringBefore("@")}"
            binding.btnLogin.isEnabled = false
        }

        binding.btnLogin.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (auth.currentUser == null) {
                startActivity(Intent(this, LoginActivity::class.java))
            }
        }

        binding.btnLeaderboard.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (auth.currentUser == null) {
                Toast.makeText(this, "Please login first to view the Leaderboard", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                startActivity(Intent(this, LeaderboardActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            binding.btnLogin.text = "Logged in as ${auth.currentUser?.email?.substringBefore("@")}"
            binding.btnLogin.isEnabled = false
        }
        updateStats()
    }

    private fun updateStats() {
        val daily = prefs.videosScrolledToday
        val month = prefs.videosScrolledMonth
        val lifetime = prefs.lifetimeScrolled

        binding.txtRankTitle.text = prefs.getBrainrotRank()
        binding.txtRankDesc.text = prefs.getBrainrotRankDesc()
        
        binding.txtDailyScrolled.text = "$daily"
        binding.txtMonthScrolled.text = "$month"
        binding.txtLifetimeScrolled.text = "$lifetime"

        syncToFirestore(daily, month, lifetime)
    }

    private fun syncToFirestore(daily: Int, month: Int, lifetime: Int) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val userEmail = user.email ?: "Unknown"
            // We use the email prefix as a display name
            val displayName = userEmail.substringBefore("@")
            
            val data = hashMapOf(
                "email" to userEmail,
                "displayName" to displayName,
                "dailyScrolled" to daily,
                "monthScrolled" to month,
                "lifetimeScrolled" to lifetime,
                "lastUpdated" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            db.collection("leaderboard").document(user.uid)
                .set(data)
                .addOnSuccessListener {
                    // Synced successfully
                }
        }
    }

    private fun shareRank() {
        val rank = prefs.getBrainrotRank()
        val daily = prefs.videosScrolledToday
        val lifetime = prefs.lifetimeScrolled
        val shareText = "I'm officially '$rank' on ReelFlow! \uD83D\uDC80\nToday: $daily shorts scrolled.\nLifetime: $lifetime doom scrolls.\n\nCan you beat my Anti-Brainrot Challenge? \uD83D\uDDFF\u2728 #ReelFlow #DoomScrolling"
        
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share your Brainrot Rank")
        startActivity(shareIntent)
    }
}
