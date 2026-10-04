package com.autoscroller.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autoscroller.app.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLoginSubmit.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val pass = binding.editPassword.text.toString().trim()
            
            if (email.isNotBlank() && pass.isNotBlank()) {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                
                auth.signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Logged in as ${auth.currentUser?.email}", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            // If sign in fails, try to create an account
                            auth.createUserWithEmailAndPassword(email, pass)
                                .addOnCompleteListener(this) { createTask ->
                                    if (createTask.isSuccessful) {
                                        Toast.makeText(this, "Account created & logged in", Toast.LENGTH_SHORT).show()
                                        finish()
                                    } else {
                                        Toast.makeText(this, "Auth Failed: ${createTask.exception?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
