package com.example.driverapp.utils

import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.example.driverapp.services.LocationService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Manager for handling driver availability status and location updates
 */
object DriverAvailabilityManager {
    private const val TAG = "DriverAvailabilityMgr"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Set the driver's availability status
     */
    fun setDriverAvailability(context: Context, isAvailable: Boolean, callback: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.e(TAG, "Cannot set availability: No user logged in")
            callback(false)
            return
        }

        // Update availability in Firestore
        val updates = hashMapOf(
            "available" to isAvailable,
            "lastStatusUpdate" to System.currentTimeMillis()
        )

        // Get the driver reference
        val driverRef = firestore.collection("users").document(currentUserId)

        driverRef.update(updates as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "Driver availability set to $isAvailable")

                // If available, start location service
                if (isAvailable) {
                    updateFCMToken(currentUserId)
                    startLocationService(context)
                } else {
                    stopLocationService(context)
                }

                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating availability: ${e.message}")
                callback(false)
            }
    }

    /**
     * Update driver's FCM token for notifications
     */
    private fun updateFCMToken(userId: String) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                firestore.collection("users").document(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM token updated successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error updating FCM token: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting FCM token: ${e.message}")
            }
    }

    /**
     * Start the location service to track driver position
     */
    private fun startLocationService(context: Context) {
        try {
            val serviceIntent = Intent(context, LocationService::class.java)
            context.startService(serviceIntent)
            Log.d(TAG, "Location service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location service: ${e.message}")
        }
    }

    /**
     * Stop the location service
     */
    private fun stopLocationService(context: Context) {
        try {
            val serviceIntent = Intent(context, LocationService::class.java)
            context.stopService(serviceIntent)
            Log.d(TAG, "Location service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location service: ${e.message}")
        }
    }

    /**
     * Check if driver is currently available
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
                Log.e(TAG, "Error checking availability: ${e.message}")
                callback(false)
            }
    }

    /**
     * Update driver's location in Firestore
     */
    fun updateDriverLocation(location: Location, callback: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            callback(false)
            return
        }

        val locationData = hashMapOf(
            "location" to GeoPoint(location.latitude, location.longitude),
            "heading" to location.bearing,
            "speed" to location.speed,
            "lastLocationUpdate" to System.currentTimeMillis()
        )

        firestore.collection("users").document(currentUserId)
            .update(locationData as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
                callback(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating location: ${e.message}")
                callback(false)
            }
    }
}