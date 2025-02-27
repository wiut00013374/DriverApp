package com.example.driverapp.data

/**
 * Data class representing a driver.
 * This model is designed to be compatible with the customer app's expectations.
 */
data class Driver(
    val uid: String = "",                    // Unique identifier (Firebase Auth UID)
    val email: String = "",                  // Email address
    val driverName: String = "",            // Driver's full name
    val phoneNumber: String = "",            // Contact phone number
    val truckType: String = "",              // Type of truck (Small, Medium, Large, etc.)
    val licensePlate: String = "",           // Vehicle license plate
    val available: Boolean = false,          // Driver availability status
    val location: DriverLocation = DriverLocation(), // Current driver location
    val rating: Float = 0.0f,                // Driver rating (0-5 stars)
    val completedOrders: Int = 0,            // Number of completed orders
    val fcmToken: String = "",               // Firebase Cloud Messaging token for notifications
    val userType: String = "driver",         // User type (always "driver")
    val createdAt: Long = System.currentTimeMillis() // Account creation timestamp
)

/**
 * Location data for the driver.
 */
data class DriverLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val heading: Float = 0.0f,               // Direction (0-359 degrees)
    val speed: Float = 0.0f,                 // Speed in km/h
    val timestamp: Long = System.currentTimeMillis(), // When this location was recorded
    val address: String = ""                 // Optional reverse-geocoded address
)