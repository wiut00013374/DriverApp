package com.example.driverapp.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.driverapp.services.LocationService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Utility class to manage driver availability and handle location tracking
 */
object DriverAvailabilityManager {
    private const val TAG = "DriverAvailabilityMgr"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Set the driver to available/unavailable state
     */
    fun setDriverAvailability(context: Context, isAvailable: Boolean, callback: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            callback(false)
            return
        }

        // If setting to available, check for location permission
        if (isAvailable) {
            if (!hasLocationPermission(context)) {
                Log.e(TAG, "Cannot set driver available without location permissions")
                callback(false)
                return
            }
        }

        // Update availability status in Firestore
        val availabilityData = hashMapOf(
            "available" to isAvailable,
            "lastStatusUpdate" to System.currentTimeMillis()
        )

        // Try updating the driver document first
        firestore.collection("users").document(currentUserId)
            .update(availabilityData as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "Driver availability updated successfully: $isAvailable")

                // Start or stop location service based on availability
                handleLocationService(context, isAvailable)

                // Update FCM token if becoming available
                if (isAvailable) {
                    updateFcmToken(currentUserId)
                }

                callback(true)
            }
            .addOnFailureListener { e ->
                // If that fails, try creating the document
                Log.d(TAG, "No driver document found, creating one")

                // Add more driver details
                val driverData = hashMapOf<String, Any>(
                    "uid" to currentUserId,
                    "userType" to "driver",
                    "available" to isAvailable,
                    "lastStatusUpdate" to System.currentTimeMillis(),
                    "createdAt" to System.currentTimeMillis()
                )

                // Get user's display name and email if available
                val user = auth.currentUser
                if (user != null) {
                    driverData["email"] = user.email ?: ""
                    driverData["displayName"] = user.displayName ?: ""
                }

                // Create the document
                firestore.collection("users").document(currentUserId)
                    .set(driverData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Driver document created successfully")

                        // Start or stop location service based on availability
                        handleLocationService(context, isAvailable)

                        // Update FCM token if becoming available
                        if (isAvailable) {
                            updateFcmToken(currentUserId)
                        }

                        callback(true)
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Error creating driver document: ${e2.message}")
                        callback(false)
                    }
            }
    }

    /**
     * Start or stop the location service based on availability
     */
    private fun handleLocationService(context: Context, isAvailable: Boolean) {
        val serviceIntent = Intent(context, LocationService::class.java)

        if (isAvailable) {
            // Start the service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Location service started")
        } else {
            // Stop the service
            context.stopService(serviceIntent)
            Log.d(TAG, "Location service stopped")
        }
    }

    /**
     * Update location data for the driver
     */
    fun updateDriverLocation(latitude: Double, longitude: Double, callback: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            callback(false)
            return
        }

        // Create location data
        val locationData = hashMapOf<String, Any>(
            "location" to GeoPoint(latitude, longitude),
            "lastLocationUpdate" to System.currentTimeMillis()
        )

        // Update in Firestore
        firestore.collection("users").document(currentUserId)
            .update(locationData)
            .addOnSuccessListener {
                Log.d(TAG, "Driver location updated successfully")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating driver location: ${e.message}")
                callback(false)
            }
    }

    /**
     * Update the FCM token for the driver to receive notifications
     */
    private fun updateFcmToken(userId: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result

                // Store the token in Firestore
                firestore.collection("users").document(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM token updated successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error updating FCM token: ${e.message}")
                    }
            } else {
                Log.e(TAG, "Failed to get FCM token: ${task.exception?.message}")
            }
        }
    }

    /**
     * Check if we have location permission
     */
    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if the driver is currently available
     */
    fun checkDriverAvailability(callback: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            callback(false)
            return
        }

        firestore.collection("users").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                val isAvailable = document.getBoolean("available") ?: false
                callback(isAvailable)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking driver availability: ${e.message}")
                callback(false)
            }
    }
}