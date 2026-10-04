package com.autoscroller.app.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autoscroller.app.data.PreferencesManager
import com.autoscroller.app.databinding.ActivityLeaderboardBinding

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)

        populateLeaderboardMock()

        binding.btnStartChallenge.setOnClickListener {
            Toast.makeText(this, "Anti-Brainrot Challenge Started! Stay under 50 videos today.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun populateLeaderboardMock() {
        val mockData = listOf(
            Triple(1, "SigmaScroller99", 5),
            Triple(2, "AuraMaster", 8),
            Triple(3, "GrassToucher", 12),
            Triple(4, "GuestUser (You)", prefs.videosScrolledToday),
            Triple(5, "DoomScrollKing", 450)
        )

        for (row in mockData.sortedBy { it.third }) {
            val view = LinearLayout(this)
            view.orientation = LinearLayout.HORIZONTAL
            view.setPadding(0, 16, 0, 16)
            
            val rankText = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = "#${row.first}"
                setTextColor(Color.parseColor("#8E99A8"))
                textSize = 16f
            }

            val userText = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
                text = row.second
                setTextColor(Color.WHITE)
                textSize = 16f
            }

            val scoreText = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
                text = "${row.third}"
                gravity = android.view.Gravity.END
                setTextColor(Color.parseColor("#00F59B")) // Neon green
                textSize = 16f
            }

            if (row.second.contains("You")) {
                userText.setTextColor(Color.parseColor("#00E5FF")) // Neon Cyan
            }

            view.addView(rankText)
            view.addView(userText)
            view.addView(scoreText)

            binding.leaderboardContainer.addView(view)
        }
    }
}
