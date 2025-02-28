package com.example.driverapp.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.driverapp.R
import com.example.driverapp.login.DriverSignInActivity
import com.example.driverapp.services.LocationService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ProfileFragment : Fragment() {

    private val TAG = "ProfileFragment"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // UI elements
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvTruckType: TextView
    private lateinit var tvLicensePlate: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnLogout: Button
    private lateinit var switchAvailability: Switch
    private lateinit var tvAvailabilityStatus: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI elements
        tvName = view.findViewById(R.id.textViewFullName)
        tvEmail = view.findViewById(R.id.textViewEmail)
        tvPhone = view.findViewById(R.id.textViewPhoneNumber)
        tvTruckType = view.findViewById(R.id.textViewTruckType)
        tvLicensePlate = view.findViewById(R.id.textViewLicensePlate)
        tvStatus = view.findViewById(R.id.textViewStatus)

        // Initialize availability switch
        switchAvailability = view.findViewById(R.id.switchAvailability)
        tvAvailabilityStatus = view.findViewById(R.id.tvAvailabilityStatus)

        // Add logout button
        btnLogout = Button(requireContext()).apply {
            text = "Logout"
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                auth.signOut()
                val intent = Intent(requireContext(), DriverSignInActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
        }

        // Add the button to layout
        val linearLayout = view.findViewById<ViewGroup>(R.id.linearLayoutProfile)
        linearLayout.addView(btnLogout)

        // Load driver profile data
        loadUserProfile()

        // Set up availability switch listener
        switchAvailability.setOnCheckedChangeListener { _, isChecked ->
            updateDriverAvailability(isChecked)
        }
    }

    private fun loadUserProfile() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Try to load from "users" collection
        firestore.collection("users")
            .document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    Log.d(TAG, "User document data: ${document.data}")

                    // Display user data
                    tvName.text = document.getString("displayName") ?: "Name not set"
                    tvEmail.text = auth.currentUser?.email ?: "Email not available"
                    tvPhone.text = document.getString("phoneNumber") ?: "Phone not set"
                    tvTruckType.text = document.getString("truckType") ?: "Truck type not set"
                    tvLicensePlate.text = document.getString("licensePlate") ?: "License plate not set"

                    // Get availability status
                    val isAvailable = document.getBoolean("available") ?: false
                    switchAvailability.isChecked = isAvailable
                    updateAvailabilityStatusText(isAvailable)

                    tvStatus.text = if (isAvailable) "Available" else "Not Available"

                    Toast.makeText(requireContext(), "Profile loaded successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "No user document found, checking drivers collection")

                    // If not found in users collection, try the drivers collection
                    firestore.collection("drivers")
                        .document(currentUserId)
                        .get()
                        .addOnSuccessListener { driverDoc ->
                            if (driverDoc != null && driverDoc.exists()) {
                                Log.d(TAG, "Driver document data: ${driverDoc.data}")

                                // Display driver data
                                tvName.text = driverDoc.getString("displayName") ?: "Name not set"
                                tvEmail.text = auth.currentUser?.email ?: "Email not available"
                                tvPhone.text = driverDoc.getString("phoneNumber") ?: "Phone not set"
                                tvTruckType.text = driverDoc.getString("truckType") ?: "Truck type not set"
                                tvLicensePlate.text = driverDoc.getString("licensePlate") ?: "License plate not set"

                                // Get availability status
                                val isAvailable = driverDoc.getBoolean("available") ?: false
                                switchAvailability.isChecked = isAvailable
                                updateAvailabilityStatusText(isAvailable)

                                tvStatus.text = if (isAvailable) "Available" else "Not Available"

                                Toast.makeText(requireContext(), "Profile loaded from drivers collection", Toast.LENGTH_SHORT).show()
                            } else {
                                // No profile found in either collection
                                Log.d(TAG, "No profile found in users or drivers collection")
                                tvName.text = "No profile data found"
                                tvEmail.text = auth.currentUser?.email ?: "Email not available"
                                switchAvailability.isChecked = false
                                updateAvailabilityStatusText(false)

                                Toast.makeText(requireContext(), "No profile data found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error getting driver document: ${e.message}")
                            Toast.makeText(requireContext(), "Error loading driver profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting user document: ${e.message}")
                Toast.makeText(requireContext(), "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateDriverAvailability(isAvailable: Boolean) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading indicator
        tvAvailabilityStatus.text = "Updating status..."
        switchAvailability.isEnabled = false

        // Check location permission if becoming available
        if (isAvailable && !hasLocationPermission()) {
            requestLocationPermission()
            switchAvailability.isChecked = false
            updateAvailabilityStatusText(false)
            switchAvailability.isEnabled = true
            return
        }

        // Start or stop location service based on availability
        if (isAvailable) {
            startLocationService()
        } else {
            stopLocationService()
        }

        // Update availability in Firestore
        val availabilityUpdate = hashMapOf<String, Any>("available" to isAvailable)

        // Try to update in users collection
        firestore.collection("users")
            .document(currentUserId)
            .update(availabilityUpdate)
            .addOnSuccessListener {
                Log.d(TAG, "Availability updated successfully in users collection")
                updateAvailabilityStatusText(isAvailable)
                switchAvailability.isEnabled = true
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating availability in users: ${e.message}")

                // Try drivers collection as fallback
                firestore.collection("drivers")
                    .document(currentUserId)
                    .update(availabilityUpdate)
                    .addOnSuccessListener {
                        Log.d(TAG, "Availability updated successfully in drivers collection")
                        updateAvailabilityStatusText(isAvailable)
                        switchAvailability.isEnabled = true
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Error updating availability in drivers: ${e2.message}")

                        // If update fails in both collections, try to set the document
                        val driverData = hashMapOf<String, Any>(
                            "uid" to currentUserId,
                            "available" to isAvailable,
                            "userType" to "driver",
                            "updatedAt" to System.currentTimeMillis()
                        )

                        firestore.collection("users")
                            .document(currentUserId)
                            .set(driverData, SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d(TAG, "Created driver availability document")
                                updateAvailabilityStatusText(isAvailable)
                                switchAvailability.isEnabled = true
                            }
                            .addOnFailureListener { e3 ->
                                Log.e(TAG, "Failed to create driver availability: ${e3.message}")
                                switchAvailability.isChecked = !isAvailable
                                updateAvailabilityStatusText(!isAvailable)
                                switchAvailability.isEnabled = true
                                Toast.makeText(requireContext(), "Failed to update availability", Toast.LENGTH_SHORT).show()
                            }
                    }
            }
    }

    private fun updateAvailabilityStatusText(isAvailable: Boolean) {
        tvAvailabilityStatus.text = if (isAvailable) {
            "You are available for orders"
        } else {
            "You are not available for orders"
        }
        tvStatus.text = if (isAvailable) "Available" else "Not Available"
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
        Toast.makeText(requireContext(), "Location permission required to become available", Toast.LENGTH_LONG).show()
    }

    private fun startLocationService() {
        val serviceIntent = Intent(requireContext(), LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }
        Log.d(TAG, "Started location service")
    }

    private fun stopLocationService() {
        val serviceIntent = Intent(requireContext(), LocationService::class.java)
        requireContext().stopService(serviceIntent)
        Log.d(TAG, "Stopped location service")
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 123
    }
}