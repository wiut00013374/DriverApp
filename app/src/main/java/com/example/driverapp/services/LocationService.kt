package com.example.driverapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.driverapp.MainActivity
import com.example.driverapp.R
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    private val UPDATE_INTERVAL = 5000L // 5 seconds
    private val FASTEST_INTERVAL = 3000L // 3 seconds

    // Current assigned order ID (if any)
    private var currentOrderId: String? = null

    // Track the current driver state
    private var isPickedUp = false
    private var isDelivered = false

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

                    // If there's an active order, check for proximity to pickup/delivery location
                    currentOrderId?.let { orderId ->
                        checkProximityToPickupOrDelivery(location, orderId)
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
        when (intent?.action) {
            ACTION_START_LOCATION_SERVICE -> {
                // Extract the order ID if provided
                currentOrderId = intent.getStringExtra(EXTRA_ORDER_ID)
                Log.d(TAG, "Starting location service for order: $currentOrderId")
            }
            ACTION_STOP_LOCATION_SERVICE -> {
                Log.d(TAG, "Stopping location service")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ORDER_PICKED_UP -> {
                currentOrderId = intent.getStringExtra(EXTRA_ORDER_ID)
                if (currentOrderId != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        markOrderAsPickedUp(currentOrderId!!)
                    }
                }
            }
            ACTION_ORDER_DELIVERED -> {
                currentOrderId = intent.getStringExtra(EXTRA_ORDER_ID)
                if (currentOrderId != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        markOrderAsDelivered(currentOrderId!!)
                    }
                }
            }
        }

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
                    mapOf(
                        "driverLocation" to geoPoint,
                        "driverHeading" to location.bearing,
                        "driverSpeed" to location.speed,
                        "driverLastUpdate" to System.currentTimeMillis()
                    )
                )
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating driver location in order: ${e.message}")
                }
        }
    }

    /**
     * Check if the driver is close to the pickup or delivery location
     */
    private fun checkProximityToPickupOrDelivery(location: Location, orderId: String) {
        firestore.collection("orders").document(orderId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val orderStatus = document.getString("status") ?: ""

                    // Track if we've already picked up or delivered
                    isPickedUp = orderStatus == "Picked Up" || orderStatus == "Delivered" || orderStatus == "Completed"
                    isDelivered = orderStatus == "Delivered" || orderStatus == "Completed"

                    if (orderStatus == "Accepted" || orderStatus == "In Progress") {
                        // Check proximity to pickup location
                        val originLat = document.getDouble("originLat") ?: 0.0
                        val originLon = document.getDouble("originLon") ?: 0.0

                        // Calculate distance to pickup location
                        val pickupLocation = Location("pickup")
                        pickupLocation.latitude = originLat
                        pickupLocation.longitude = originLon

                        val distanceToPickup = location.distanceTo(pickupLocation) / 1000.0 // Convert to kilometers

                        Log.d(TAG, "Distance to pickup: $distanceToPickup km")

                        // Update the distance to pickup in the order document
                        val canPickup = distanceToPickup <= PICKUP_RADIUS_KM
                        firestore.collection("orders").document(orderId)
                            .update(
                                mapOf(
                                    "distanceToPickup" to distanceToPickup,
                                    "driverCanPickup" to canPickup,
                                    "pickupEta" to calculateETA(distanceToPickup, location.speed)
                                )
                            )
                            .addOnSuccessListener {
                                // If status is just "Accepted", update to "In Progress"
                                // once location updates start flowing
                                if (orderStatus == "Accepted") {
                                    firestore.collection("orders").document(orderId)
                                        .update("status", "In Progress")
                                }

                                // Update the notification with the new proximity information
                                updateNotificationWithProximity(canPickup, false)
                            }

                    } else if (orderStatus == "Picked Up") {
                        // Check proximity to delivery location
                        val destLat = document.getDouble("destinationLat") ?: 0.0
                        val destLon = document.getDouble("destinationLon") ?: 0.0

                        // Calculate distance to delivery location
                        val deliveryLocation = Location("delivery")
                        deliveryLocation.latitude = destLat
                        deliveryLocation.longitude = destLon

                        val distanceToDelivery = location.distanceTo(deliveryLocation) / 1000.0 // Convert to kilometers

                        Log.d(TAG, "Distance to delivery: $distanceToDelivery km")

                        // Update the distance to delivery in the order document
                        val canDeliver = distanceToDelivery <= DELIVERY_RADIUS_KM
                        firestore.collection("orders").document(orderId)
                            .update(
                                mapOf(
                                    "distanceToDelivery" to distanceToDelivery,
                                    "driverCanDeliver" to canDeliver,
                                    "deliveryEta" to calculateETA(distanceToDelivery, location.speed)
                                )
                            )
                            .addOnSuccessListener {
                                // Update the notification with the new proximity information
                                updateNotificationWithProximity(false, canDeliver)
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting order data: ${e.message}")
            }
    }

    /**
     * Calculate estimated time of arrival based on distance and speed
     */
    private fun calculateETA(distanceKm: Double, speedMps: Float): Long {
        // If speed is too slow or not available, assume a default speed of 40 km/h
        val speedKmh = if (speedMps < 2.0f) 40.0f else speedMps * 3.6f

        // Calculate hours to arrival
        val hoursToArrival = distanceKm / speedKmh

        // Convert to milliseconds
        val etaMillis = (hoursToArrival * 3600 * 1000).toLong()

        // Return the estimated arrival time (current time + eta)
        return System.currentTimeMillis() + etaMillis
    }

    /**
     * Mark the order as picked up
     */
    private suspend fun markOrderAsPickedUp(orderId: String) {
        try {
            // First check if the driver is close enough to pickup
            val orderDoc = firestore.collection("orders").document(orderId).get().await()
            val canPickup = orderDoc.getBoolean("driverCanPickup") ?: false

            if (!canPickup) {
                Log.d(TAG, "Driver is not close enough to pickup location")
                NotificationService.sendDriverNotification(
                    this,
                    "Cannot Mark as Picked Up",
                    "You must be within 10km of the pickup location"
                )
                return
            }

            // Update the order status
            firestore.collection("orders").document(orderId)
                .update(
                    mapOf(
                        "status" to "Picked Up",
                        "pickedUpAt" to System.currentTimeMillis()
                    )
                )
                .await()

            // Mark the order as picked up locally
            isPickedUp = true

            // Update the notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())

            // Notify the customer
            val customerUid = orderDoc.getString("uid")
            if (customerUid != null) {
                NotificationService.sendCustomerOrderUpdate(
                    this,
                    customerUid,
                    orderId,
                    "Order Picked Up",
                    "Your order has been picked up and is on the way to the delivery location"
                )
            }

            Log.d(TAG, "Order $orderId marked as picked up")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking order as picked up: ${e.message}")
        }
    }

    /**
     * Mark the order as delivered
     */
    private suspend fun markOrderAsDelivered(orderId: String) {
        try {
            // First check if the driver is close enough to delivery
            val orderDoc = firestore.collection("orders").document(orderId).get().await()
            val canDeliver = orderDoc.getBoolean("driverCanDeliver") ?: false

            if (!canDeliver) {
                Log.d(TAG, "Driver is not close enough to delivery location")
                NotificationService.sendDriverNotification(
                    this,
                    "Cannot Mark as Delivered",
                    "You must be within 10km of the delivery location"
                )
                return
            }

            // Update the order status
            firestore.collection("orders").document(orderId)
                .update(
                    mapOf(
                        "status" to "Delivered",
                        "deliveredAt" to System.currentTimeMillis()
                    )
                )
                .await()

            // Mark the order as delivered locally
            isDelivered = true

            // Update the notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification())

            // Notify the customer
            val customerUid = orderDoc.getString("uid")
            if (customerUid != null) {
                NotificationService.sendCustomerOrderUpdate(
                    this,
                    customerUid,
                    orderId,
                    "Order Delivered",
                    "Your order has been delivered to the destination"
                )
            }

            Log.d(TAG, "Order $orderId marked as delivered")

            // After a delivered order, we stop the service
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error marking order as delivered: ${e.message}")
        }
    }

    /**
     * Update the notification with proximity information
     */
    private fun updateNotificationWithProximity(canPickup: Boolean, canDeliver: Boolean) {
        if (currentOrderId != null) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification(canPickup, canDeliver))
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
    private fun createNotification(canPickup: Boolean = false, canDeliver: Boolean = false): Notification {
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

        // Create intent for opening the main activity
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification with different content based on whether there's an active order
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)

        if (currentOrderId != null) {
            // Determine notification content based on order status
            if (isDelivered) {
                notificationBuilder
                    .setContentTitle("Order Delivered")
                    .setContentText("Order has been successfully delivered")
            } else if (isPickedUp) {
                val deliveryText = if (canDeliver) "You are in delivery zone! You can mark the order as delivered."
                else "Heading to delivery location. Your location is being shared."
                notificationBuilder
                    .setContentTitle("Order Picked Up")
                    .setContentText(deliveryText)
            } else {
                val pickupText = if (canPickup) "You are in pickup zone! You can mark the order as picked up."
                else "Heading to pickup location. Your location is being shared."
                notificationBuilder
                    .setContentTitle("Active Order Navigation")
                    .setContentText(pickupText)
            }
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

        // Action constants
        const val ACTION_START_LOCATION_SERVICE = "com.example.driverapp.START_LOCATION_SERVICE"
        const val ACTION_STOP_LOCATION_SERVICE = "com.example.driverapp.STOP_LOCATION_SERVICE"
        const val ACTION_ORDER_PICKED_UP = "com.example.driverapp.ORDER_PICKED_UP"
        const val ACTION_ORDER_DELIVERED = "com.example.driverapp.ORDER_DELIVERED"

        // Constants for proximity thresholds
        private const val PICKUP_RADIUS_KM = 10.0
        private const val DELIVERY_RADIUS_KM = 10.0

        /**
         * Start the location service for an active order
         */
        fun startForOrder(context: Context, orderId: String) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_START_LOCATION_SERVICE
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
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_START_LOCATION_SERVICE
            }

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
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_STOP_LOCATION_SERVICE
            }
            context.startService(intent)
        }

        /**
         * Mark order as picked up
         */
        fun markOrderPickedUp(context: Context, orderId: String) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_ORDER_PICKED_UP
                putExtra(EXTRA_ORDER_ID, orderId)
            }
            context.startService(intent)
        }

        /**
         * Mark order as delivered
         */
        fun markOrderDelivered(context: Context, orderId: String) {
            val intent = Intent(context, LocationService::class.java).apply {
                action = ACTION_ORDER_DELIVERED
                putExtra(EXTRA_ORDER_ID, orderId)
            }
            context.startService(intent)
        }
    }
}