package com.example.driverapp.services

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.driverapp.data.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.*

/**
 * Service to handle order status transitions and tracking
 */
class OrderStatusTracker {
    companion object {
        private const val TAG = "OrderStatusTracker"
        private val firestore = FirebaseFirestore.getInstance()
        private val auth = FirebaseAuth.getInstance()

        // Max distance from pickup/delivery points to enable status change
        private const val PICKUP_RADIUS_KM = 10.0 // 10 kilometers
        private const val DELIVERY_RADIUS_KM = 10.0 // 10 kilometers

        /**
         * Get the current driver's location
         */
        suspend fun getCurrentDriverLocation(): GeoPoint? {
            val driverId = auth.currentUser?.uid ?: return null

            try {
                val driverDoc = firestore.collection("users")
                    .document(driverId)
                    .get()
                    .await()

                val locationObj = driverDoc.get("location") as? GeoPoint
                return locationObj
            } catch (e: Exception) {
                Log.e(TAG, "Error getting driver location: ${e.message}")
                return null
            }
        }

        /**
         * Calculate distance between two points in kilometers using Haversine formula
         */
        fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val R = 6371.0 // Earth's radius in kilometers
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return R * c
        }

        /**
         * Check if the driver is close enough to pick up the order
         */
        suspend fun canPickUpOrder(orderId: String): Boolean {
            try {
                val orderDoc = firestore.collection("orders")
                    .document(orderId)
                    .get()
                    .await()

                // Get the order's pickup location
                val pickupLat = orderDoc.getDouble("originLat") ?: return false
                val pickupLon = orderDoc.getDouble("originLon") ?: return false

                // Get the driver's current location
                val driverLocation = getCurrentDriverLocation() ?: return false

                // Calculate distance between driver and pickup point
                val distance = calculateDistance(
                    driverLocation.latitude, driverLocation.longitude,
                    pickupLat, pickupLon
                )

                Log.d(TAG, "Distance to pickup: $distance km")

                // Return true if driver is within pickup radius
                return distance <= PICKUP_RADIUS_KM
            } catch (e: Exception) {
                Log.e(TAG, "Error checking pickup proximity: ${e.message}")
                return false
            }
        }

        /**
         * Check if the driver is close enough to deliver the order
         */
        suspend fun canDeliverOrder(orderId: String): Boolean {
            try {
                val orderDoc = firestore.collection("orders")
                    .document(orderId)
                    .get()
                    .await()

                // Get the order's delivery location
                val deliveryLat = orderDoc.getDouble("destinationLat") ?: return false
                val deliveryLon = orderDoc.getDouble("destinationLon") ?: return false

                // Get the driver's current location
                val driverLocation = getCurrentDriverLocation() ?: return false

                // Calculate distance between driver and delivery point
                val distance = calculateDistance(
                    driverLocation.latitude, driverLocation.longitude,
                    deliveryLat, deliveryLon
                )

                Log.d(TAG, "Distance to delivery: $distance km")

                // Return true if driver is within delivery radius
                return distance <= DELIVERY_RADIUS_KM
            } catch (e: Exception) {
                Log.e(TAG, "Error checking delivery proximity: ${e.message}")
                return false
            }
        }

        /**
         * Update order status to "Picked Up"
         */
        suspend fun updateOrderToPickedUp(context: Context, orderId: String): Boolean {
            try {
                // Check if the driver is close enough to pickup location
                if (!canPickUpOrder(orderId)) {
                    Log.d(TAG, "Cannot pick up order: Driver is too far from pickup location")
                    return false
                }

                // Update the order status to "Picked Up"
                firestore.collection("orders")
                    .document(orderId)
                    .update(
                        mapOf(
                            "status" to "Picked Up",
                            "pickedUpAt" to System.currentTimeMillis()
                        )
                    )
                    .await()

                Log.d(TAG, "Order $orderId marked as picked up")

                // Notify the customer
                notifyCustomerAboutPickup(context, orderId)

                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error updating order to picked up: ${e.message}")
                return false
            }
        }

        /**
         * Update order status to "Delivered"
         */
        suspend fun updateOrderToDelivered(context: Context, orderId: String): Boolean {
            try {
                // Check if the driver is close enough to delivery location
                if (!canDeliverOrder(orderId)) {
                    Log.d(TAG, "Cannot mark as delivered: Driver is too far from delivery location")
                    return false
                }

                // Update the order status to "Delivered"
                firestore.collection("orders")
                    .document(orderId)
                    .update(
                        mapOf(
                            "status" to "Delivered",
                            "deliveredAt" to System.currentTimeMillis()
                        )
                    )
                    .await()

                Log.d(TAG, "Order $orderId marked as delivered")

                // Notify the customer
                notifyCustomerAboutDelivery(context, orderId)

                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error updating order to delivered: ${e.message}")
                return false
            }
        }

        /**
         * Notify the customer that their order has been picked up
         */
        private fun notifyCustomerAboutPickup(context: Context, orderId: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Get the order details
                    val orderDoc = firestore.collection("orders")
                        .document(orderId)
                        .get()
                        .await()

                    val customerUid = orderDoc.getString("uid")
                    if (customerUid != null) {
                        // Get the customer's FCM token
                        val customerDoc = firestore.collection("users")
                            .document(customerUid)
                            .get()
                            .await()

                        val fcmToken = customerDoc.getString("fcmToken")

                        if (fcmToken != null) {
                            // Send the notification
                            NotificationService.sendCustomerOrderNotification(
                                context,
                                fcmToken,
                                orderId,
                                "Order Picked Up",
                                "Your order has been picked up and is on the way to the destination."
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying customer about pickup: ${e.message}")
                }
            }
        }

        /**
         * Notify the customer that their order has been delivered
         */
        private fun notifyCustomerAboutDelivery(context: Context, orderId: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Get the order details
                    val orderDoc = firestore.collection("orders")
                        .document(orderId)
                        .get()
                        .await()

                    val customerUid = orderDoc.getString("uid")
                    if (customerUid != null) {
                        // Get the customer's FCM token
                        val customerDoc = firestore.collection("users")
                            .document(customerUid)
                            .get()
                            .await()

                        val fcmToken = customerDoc.getString("fcmToken")

                        if (fcmToken != null) {
                            // Send the notification
                            NotificationService.sendCustomerOrderNotification(
                                context,
                                fcmToken,
                                orderId,
                                "Order Delivered",
                                "Your order has been delivered successfully!"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying customer about delivery: ${e.message}")
                }
            }
        }
    }
}