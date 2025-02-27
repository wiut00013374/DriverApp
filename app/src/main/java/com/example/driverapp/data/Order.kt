package com.example.driverapp.data

data class Order(
    var id: String = "",
    val uid: String = "", // Customer UID
    var driverUid: String? = null, // Assigned Driver UID
    var originCity: String = "",
    var destinationCity: String = "",
    var originLat: Double = 0.0,
    var originLon: Double = 0.0,
    var destinationLat: Double = 0.0,
    var destinationLon: Double = 0.0,
    var totalPrice: Double = 0.0,
    var truckType: String = "",
    var volume: Double = 0.0,
    var weight: Double = 0.0,
    var status: String = "pending", // Key change:  pending, assigned, in_transit, completed, cancelled
    val driversContacted: MutableMap<String, String> = mutableMapOf(), // Track contacted drivers
    val timestamp: Long = System.currentTimeMillis() // Add a timestamp
)