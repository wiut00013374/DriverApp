package com.example.driverapp.data

data class Driver(
    val driverId: String = "",
    var available: Boolean = false,
    val name: String = "",
    val location: Location = Location(), //  For future use
    val email: String = "",
    val phoneNumber: String = "",
    val truckType: String = "",
    val licensePlate: String = "",
)
data class Location(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = ""
)