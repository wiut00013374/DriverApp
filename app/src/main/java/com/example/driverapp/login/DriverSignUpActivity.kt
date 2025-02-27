// login/DriverSignUpActivity.kt
package com.example.driverapp.login

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.driverapp.MainActivity
import com.example.driverapp.R
import com.example.driverapp.data.Driver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase // Import Realtime Database

class DriverSignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase // Realtime Database


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_sign_up)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance() // Initialize Realtime Database


        val etEmail = findViewById<EditText>(R.id.etDriverEmail)
        val etPassword = findViewById<EditText>(R.id.etDriverPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etDriverConfirmPassword)
        val etPhoneNumber = findViewById<EditText>(R.id.etDriverPhone)
        val etTruckType = findViewById<EditText>(R.id.etDriverTruckType)
        val etLicensePlate = findViewById<EditText>(R.id.etDriverLicensePlate)
        val etFullName = findViewById<EditText>(R.id.etDriverFullName)
        val btnSignUp = findViewById<Button>(R.id.btnDriverSignUp)

        btnSignUp.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()
            val phoneNumber = etPhoneNumber.text.toString().trim()
            val truckType = etTruckType.text.toString().trim()
            val licensePlate = etLicensePlate.text.toString().trim()
            val fullName = etFullName.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() ||
                phoneNumber.isEmpty() || truckType.isEmpty() || licensePlate.isEmpty() || fullName.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign up success, now store driver data in Realtime DB
                        val user = auth.currentUser
                        val driver = Driver(
                            driverId = user!!.uid, // Use the UID as the driver ID
                            email = email,
                            phoneNumber = phoneNumber,
                            truckType = truckType,
                            licensePlate = licensePlate, // You might want to encrypt this
                            name = fullName,
                            available = false // Initially not available

                        )

                        database.getReference("drivers/${user.uid}")
                            .setValue(driver)
                            .addOnSuccessListener {
                                // Send email verification
                                user.sendEmailVerification()
                                    .addOnCompleteListener { verificationTask ->
                                        if (verificationTask.isSuccessful) {
                                            Toast.makeText(baseContext, "Verification email sent to $email", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(baseContext, "Failed to send verification email.", Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                Toast.makeText(baseContext, "Driver registered successfully", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@DriverSignUpActivity, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                // Handle database write failure
                                Toast.makeText(this, "Database error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        // If sign up fails, display a message to the user.
                        Toast.makeText(baseContext, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}