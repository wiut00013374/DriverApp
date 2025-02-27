package com.example.driverapp.login

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.*
import com.example.driverapp.MainActivity
import com.example.driverapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase // Import Realtime Database

class DriverSignInActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase // Realtime Database

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_sign_in)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance() // Initialize Realtime Database

        val etEmail = findViewById<EditText>(R.id.etDriverSignInEmail)
        val etPassword = findViewById<EditText>(R.id.etDriverSignInPassword)
        val btnSignIn = findViewById<Button>(R.id.btnDriverSignIn)
        val tvSignUpLink = findViewById<TextView>(R.id.tvDriverSignUpLink)

        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val intent = Intent(this, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this, "Sign In Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter Email and Password", Toast.LENGTH_SHORT).show()
            }
        }

        tvSignUpLink.setOnClickListener {
            val intent = Intent(this, DriverSignUpActivity::class.java)
            startActivity(intent)
        }
    }
}