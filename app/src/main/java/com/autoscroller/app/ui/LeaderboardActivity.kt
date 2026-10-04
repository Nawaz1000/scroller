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

        loadLeaderboard()

        binding.btnStartChallenge.setOnClickListener {
            Toast.makeText(this, "Anti-Brainrot Challenge Started! Stay under 50 videos today.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun loadLeaderboard() {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        // Query the top 50 users sorted by least videos scrolled today
        db.collection("leaderboard")
            .orderBy("dailyScrolled", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { result ->
                binding.leaderboardContainer.removeAllViews()
                
                var rank = 1
                for (document in result) {
                    val displayName = document.getString("displayName") ?: "Unknown"
                    val score = document.getLong("dailyScrolled")?.toInt() ?: 0
                    
                    val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    val isMe = currentUser != null && document.id == currentUser.uid
                    
                    addLeaderboardRow(rank, if (isMe) "$displayName (You)" else displayName, score, isMe)
                    rank++
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load leaderboard", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addLeaderboardRow(rank: Int, name: String, score: Int, isMe: Boolean) {
        val view = LinearLayout(this)
        view.orientation = LinearLayout.HORIZONTAL
        view.setPadding(0, 16, 0, 16)
        
        val rankText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "#$rank"
            setTextColor(Color.parseColor("#8E99A8"))
            textSize = 16f
        }

        val userText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f)
            text = name
            setTextColor(if (isMe) Color.parseColor("#00E5FF") else Color.WHITE)
            textSize = 16f
        }

        val scoreText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f)
            text = "$score"
            gravity = android.view.Gravity.END
            setTextColor(Color.parseColor("#00F59B")) // Neon green
            textSize = 16f
        }

        view.addView(rankText)
        view.addView(userText)
        view.addView(scoreText)

        binding.leaderboardContainer.addView(view)
    }
}
