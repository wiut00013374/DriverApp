package com.example.driverapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.driverapp.R
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions

/**
 * Background service that tracks driver location and updates it in Firestore
 * This service is started when a driver sets themselves as "available"
 */
class LocationService : Service() {

    private val TAG = "LocationService"
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // How often to update the driver's location (in milliseconds)
    private val UPDATE_INTERVAL = 30000L // 30 seconds
    private val FASTEST_INTERVAL = 10000L // 10 seconds

    override fun onCreate() {
        super.onCreate()

        // Create the location request
        val locationRequest = LocationRequest.create().apply {
            interval = UPDATE_INTERVAL
            fastestInterval = FASTEST_INTERVAL
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Initialize fusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Define the location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateUserLocation(location)
                }
            }
        }

        // Start in foreground with notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Request location updates
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Error requesting location updates: ${e.message}")
        }

        // Also update driver status to available
        updateDriverAvailability(true)
    }

    private fun updateUserLocation(location: Location) {
        val currentUserId = auth.currentUser?.uid ?: return

        // Create location data
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        val locationData = hashMapOf(
            "location" to geoPoint,
            "lastLocationUpdate" to System.currentTimeMillis(),
            "heading" to location.bearing,
            "speed" to location.speed
        )

        // First update the users collection (this is what the customer app checks)
        firestore.collection("users").document(currentUserId)
            .update(locationData as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "User location updated successfully in users collection")
            }
            .addOnFailureListener { e ->
                // If update fails, try to set with merge option
                firestore.collection("users").document(currentUserId)
                    .set(locationData, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Driver location set successfully in users collection")
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Error setting location in users: ${e2.message}")
                    }
            }
    }

    private fun updateDriverAvailability(isAvailable: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return

        val driverUpdate = hashMapOf<String, Any>(
            "available" to isAvailable,
            "lastStatusUpdate" to System.currentTimeMillis()
        )

        // Update in users collection
        firestore.collection("users").document(currentUserId)
            .update(driverUpdate)
            .addOnSuccessListener {
                Log.d(TAG, "Driver availability set to $isAvailable")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update availability: ${e.message}")

                // Try to set the document if update fails
                firestore.collection("users").document(currentUserId)
                    .set(driverUpdate, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Driver availability set via set() to $isAvailable")
                    }
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Failed to set availability: ${e2.message}")
                    }
            }
    }

    private fun createNotification(): Notification {
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Updates",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Build the notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Driver Location Service")
            .setContentText("You are available for orders. Your location is being shared.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If the service is killed, restart it
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop location updates
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Set driver to unavailable when service is destroyed
        updateDriverAvailability(false)
    }

    companion object {
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "location_service_channel"
    }
}