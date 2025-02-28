package com.example.driverapp.login

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.driverapp.MainActivity
import com.example.driverapp.R
import com.example.driverapp.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DriverSignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // UI Components
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var etPhone: EditText
    private lateinit var spinnerTruckType: Spinner
    private lateinit var etLicensePlate: EditText
    private lateinit var etFullName: EditText
    private lateinit var btnSignUp: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_sign_up)

        // Initialize Firebase components
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize UI components
        initializeViews()

        // Setup sign up button click listener
        btnSignUp.setOnClickListener {
            validateAndSignUp()
        }
    }

    private fun initializeViews() {
        etEmail = findViewById(R.id.etDriverEmail)
        etPassword = findViewById(R.id.etDriverPassword)
        etConfirmPassword = findViewById(R.id.etDriverConfirmPassword)
        etPhone = findViewById(R.id.etDriverPhone)
        spinnerTruckType = findViewById(R.id.spinnerDriverTruckType)
        etLicensePlate = findViewById(R.id.etDriverLicensePlate)
        etFullName = findViewById(R.id.etDriverFullName)
        btnSignUp = findViewById(R.id.btnDriverSignUp)

        // Set up Truck Type Spinner
        val truckTypes = arrayOf(
            "Select Truck Type",
            "Small",
            "Medium",
            "Large",
            "Refrigerated",
            "Flatbed"
        )
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            truckTypes
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTruckType.adapter = adapter
    }

    private fun validateAndSignUp() {
        // Collect input values
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()
        val phoneNumber = etPhone.text.toString().trim()
        val truckTypePosition = spinnerTruckType.selectedItemPosition
        val truckType = spinnerTruckType.selectedItem.toString()
        val licensePlate = etLicensePlate.text.toString().trim()
        val fullName = etFullName.text.toString().trim()

        // Validate inputs
        if (!validateInputs(
                email,
                password,
                confirmPassword,
                phoneNumber,
                truckTypePosition,
                licensePlate,
                fullName
            )
        ) {
            return
        }

        // Proceed with sign up
        signUpUser(
            email,
            password,
            phoneNumber,
            truckType,
            licensePlate,
            fullName
        )
    }

    private fun validateInputs(
        email: String,
        password: String,
        confirmPassword: String,
        phoneNumber: String,
        truckTypePosition: Int,
        licensePlate: String,
        fullName: String
    ): Boolean {
        return when {
            email.isEmpty() -> {
                etEmail.error = "Email cannot be empty"
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                etEmail.error = "Invalid email format"
                false
            }
            password.isEmpty() -> {
                etPassword.error = "Password cannot be empty"
                false
            }
            password.length < 6 -> {
                etPassword.error = "Password must be at least 6 characters"
                false
            }
            password != confirmPassword -> {
                etConfirmPassword.error = "Passwords do not match"
                false
            }
            fullName.isEmpty() -> {
                etFullName.error = "Full name cannot be empty"
                false
            }
            phoneNumber.isEmpty() -> {
                etPhone.error = "Phone number cannot be empty"
                false
            }
            truckTypePosition == 0 -> {
                Toast.makeText(this, "Please select a truck type", Toast.LENGTH_SHORT).show()
                false
            }
            licensePlate.isEmpty() -> {
                etLicensePlate.error = "License plate cannot be empty"
                false
            }
            else -> true
        }
    }

    private fun signUpUser(
        email: String,
        password: String,
        phoneNumber: String,
        truckType: String,
        licensePlate: String,
        fullName: String
    ) {
        // Show loading indicator or disable button
        btnSignUp.isEnabled = false

        // Create user in Firebase Authentication
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Get the newly created user
                    val user = auth.currentUser

                    user?.let { firebaseUser ->
                        // Create Driver object
                        val user = User(
                            uid = firebaseUser.uid,
                            email = email,
                            driverName = fullName,
                            phoneNumber = phoneNumber,
                            userType = "driver",
                            truckType = truckType,
                            licensePlate = licensePlate,
                            available = false,
                            createdAt = System.currentTimeMillis()
                        )

                        // Save driver details to Firestore
                        firestore.collection("users")
                            .document(firebaseUser.uid)
                            .set(user)
                            .addOnSuccessListener {
                                // Navigate to MainActivity
                                navigateToMainActivity()
                            }
                            .addOnFailureListener { e ->
                                // Handle Firestore save failure
                                handleSignUpFailure("Failed to save driver profile: ${e.message}")
                            }
                    }
                } else {
                    // Handle authentication failure
                    handleSignUpFailure(task.exception?.message ?: "Sign up failed")
                }
            }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun handleSignUpFailure(errorMessage: String) {
        // Re-enable sign up button
        btnSignUp.isEnabled = true

        // Show error message
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }
}