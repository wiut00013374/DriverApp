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
 * This service is started when a driver sets themselves as "available" or accepts an order
 */
class LocationService : Service() {

    private val TAG = "LocationService"
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // How often to update the driver's location (in milliseconds)
    private val UPDATE_INTERVAL = 10000L // 10 seconds
    private val FASTEST_INTERVAL = 5000L // 5 seconds

    // Current assigned order ID (if any)
    private var currentOrderId: String? = null

    // Service lifecycle
    override fun onCreate() {
        super.onCreate()

        // Initialize fusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Create the location request with high accuracy and frequent updates
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            .build()

        // Define the location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateUserLocation(location)

                    // If there's an active order, check for proximity to pickup location
                    currentOrderId?.let { orderId ->
                        checkProximityToPickupLocation(location, orderId)
                    }
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

        // If not handling an order, update driver availability to true
        if (currentOrderId == null) {
            updateDriverAvailability(true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Extract the order ID if provided
        currentOrderId = intent?.getStringExtra(EXTRA_ORDER_ID)

        // Update the notification with the order information
        if (currentOrderId != null) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }

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

        // Set driver to unavailable when service is destroyed (unless actively on an order)
        if (currentOrderId == null) {
            updateDriverAvailability(false)
        }
    }

    /**
     * Update the driver's location in Firestore
     */
    private fun updateUserLocation(location: Location) {
        val currentUserId = auth.currentUser?.uid ?: return

        // Create location data with detailed information
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        val locationData = hashMapOf(
            "location" to geoPoint,
            "lastLocationUpdate" to System.currentTimeMillis(),
            "heading" to location.bearing,
            "speed" to location.speed,
            "accuracy" to location.accuracy
        )

        // Update the location in the user's document
        firestore.collection("users").document(currentUserId)
            .update(locationData as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
            }
            .addOnFailureListener { e ->
                // If update fails, try to set with merge option
                firestore.collection("users").document(currentUserId)
                    .set(locationData, SetOptions.merge())
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Error setting location: ${e2.message}")
                    }
            }

        // If there's an active order, update the location in the order document as well
        currentOrderId?.let { orderId ->
            firestore.collection("orders").document(orderId)
                .update(
                    "driverLocation" to geoPoint,
                    "driverHeading" to location.bearing,
                    "driverSpeed" to location.speed,
                    "driverLastUpdate" to System.currentTimeMillis()
                )
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating driver location in order: ${e.message}")
                }
        }
    }

    /**
     * Check if the driver is close to the pickup location
     */
    private fun checkProximityToPickupLocation(location: Location, orderId: String) {
        firestore.collection("orders").document(orderId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val orderStatus = document.getString("status") ?: ""

                    // Only check proximity if the order is in "Accepted" status (not yet picked up)
                    if (orderStatus == "Accepted") {
                        val originLat = document.getDouble("originLat") ?: 0.0
                        val originLon = document.getDouble("originLon") ?: 0.0

                        // Calculate distance to pickup location
                        val pickupLocation = Location("pickup")
                        pickupLocation.latitude = originLat
                        pickupLocation.longitude = originLon

                        val distanceToPickup = location.distanceTo(pickupLocation) / 1000f // Convert to kilometers

                        Log.d(TAG, "Distance to pickup: $distanceToPickup km")

                        // Update the distance to pickup in the order document
                        firestore.collection("orders").document(orderId)
                            .update("distanceToPickup", distanceToPickup)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting order data: ${e.message}")
            }
    }

    /**
     * Update the driver's availability status in Firestore
     */
    private fun updateDriverAvailability(isAvailable: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return

        val driverUpdate = hashMapOf<String, Any>(
            "available" to isAvailable,
            "lastStatusUpdate" to System.currentTimeMillis()
        )

        // Update in users collection
        firestore.collection("users").document(currentUserId)
            .update(driverUpdate)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update availability: ${e.message}")

                // Try to set the document if update fails
                firestore.collection("users").document(currentUserId)
                    .set(driverUpdate, SetOptions.merge())
                    .addOnFailureListener { e2 ->
                        Log.e(TAG, "Failed to set availability: ${e2.message}")
                    }
            }
    }

    /**
     * Create the foreground service notification
     */
    private fun createNotification(): Notification {
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Driver Location Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Updates your location in the background"
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Build the notification with different content based on whether there's an active order
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (currentOrderId != null) {
            notificationBuilder
                .setContentTitle("Active Order Navigation")
                .setContentText("Sharing your location with the customer")
        } else {
            notificationBuilder
                .setContentTitle("Driver Location Service")
                .setContentText("You are available for orders. Your location is being shared.")
        }

        return notificationBuilder.build()
    }

    companion object {
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "driver_location_service_channel"
        const val EXTRA_ORDER_ID = "order_id"

        /**
         * Start the location service for an active order
         */
        fun startForOrder(context: Context, orderId: String) {
            val intent = Intent(context, LocationService::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Start the location service for general availability
         */
        fun startForAvailability(context: Context) {
            val intent = Intent(context, LocationService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the location service
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}