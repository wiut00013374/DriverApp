package com.example.driverapp.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.tasks.await

class DriverOrderResponseHandler {
    private val TAG = "DriverOrderResponseHandler"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Handle driver's response to an order notification
     * @param orderId The ID of the order
     * @param accepted Whether the driver accepted or rejected the order
     */
    suspend fun handleOrderResponse(orderId: String, accepted: Boolean): Boolean {
        try {
            val currentUserId = auth.currentUser?.uid
                ?: throw IllegalStateException("No authenticated user")

            // Get the current order document
            val orderDoc = firestore.collection("orders").document(orderId).get().await()

            // Validate order exists and is still pending
            if (!orderDoc.exists()) {
                Log.w(TAG, "Order $orderId no longer exists")
                return false
            }

            // Check order status
            val currentStatus = orderDoc.getString("status")
            if (currentStatus != "Pending") {
                Log.w(TAG, "Order $orderId is no longer pending")
                return false
            }

            // Get the drivers contact list
            @Suppress("UNCHECKED_CAST")
            val driversContactList = orderDoc.get("driversContactList") as? Map<String, String>
                ?: throw IllegalStateException("No drivers contact list found")

            // Verify this driver was in the contact list
            if (!driversContactList.containsKey(currentUserId)) {
                Log.w(TAG, "Driver $currentUserId not in contact list for order $orderId")
                return false
            }

            return if (accepted) {
                acceptOrder(orderId, currentUserId)
            } else {
                rejectOrder(orderId, currentUserId)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling order response: ${e.message}")
            return false
        }
    }

    /**
     * Accept the order
     */
    private suspend fun acceptOrder(orderId: String, driverId: String): Boolean {
        return try {
            // Update order with driver details
            firestore.collection("orders").document(orderId)
                .update(
                    mapOf(
                        "driverUid" to driverId,
                        "status" to "Accepted",
                        "driversContactList.${driverId}" to "accepted",
                        "acceptedAt" to System.currentTimeMillis()
                    )
                )
                .await()

            Log.d(TAG, "Order $orderId accepted by driver $driverId")

            // Notify customer that a driver has been found
            notifyCustomerOrderAccepted(orderId, driverId)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting order: ${e.message}")
            false
        }
    }

    /**
     * Reject the order
     */
    private suspend fun rejectOrder(orderId: String, driverId: String): Boolean {
        return try {
            // Update driver's status in contact list
            firestore.collection("orders").document(orderId)
                .update(
                    mapOf(
                        "driversContactList.${driverId}" to "rejected"
                    )
                )
                .await()

            Log.d(TAG, "Order $orderId rejected by driver $driverId")

            // Trigger finding next driver
            triggerNextDriverSearch(orderId)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting order: ${e.message}")
            false
        }
    }

    /**
     * Trigger the process to find the next available driver
     */
    private suspend fun triggerNextDriverSearch(orderId: String) {
        try {
            // Get the current order document
            val orderDoc = firestore.collection("orders").document(orderId).get().await()

            // Get current drivers contact list
            @Suppress("UNCHECKED_CAST")
            val driversContactList = orderDoc.get("driversContactList") as? MutableMap<String, String>
                ?: return

            // Find the next uncontacted driver
            val nextDriverCandidate = driversContactList.entries
                .firstOrNull { it.value == "pending" }

            if (nextDriverCandidate != null) {
                // Update the order to notify the next driver
                firestore.collection("orders").document(orderId)
                    .update(
                        mapOf(
                            "driversContactList.${nextDriverCandidate.key}" to "notified"
                        )
                    )
                    .await()

                // Send notification to the next driver
                sendDriverNotification(orderId, nextDriverCandidate.key)
            } else {
                // No more drivers to contact
                firestore.collection("orders").document(orderId)
                    .update("status", "No Drivers Available")
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering next driver search: ${e.message}")
        }
    }

    /**
     * Send notification to the next driver
     */
    private suspend fun sendDriverNotification(orderId: String, driverId: String) {
        try {
            // Get driver's FCM token
            val driverDoc = firestore.collection("users").document(driverId).get().await()
            val fcmToken = driverDoc.getString("fcmToken")

            if (fcmToken.isNullOrBlank()) {
                Log.w(TAG, "No FCM token found for driver $driverId")
                return
            }

            // Get order details for notification
            val orderDoc = firestore.collection("orders").document(orderId).get().await()
            val order = orderDoc.toObject(Order::class.java)

            if (order == null) {
                Log.w(TAG, "Order $orderId not found")
                return
            }

            // Construct notification payload
            val notificationData = mapOf(
                "type" to "order_request",
                "orderId" to orderId,
                "originCity" to order.originCity,
                "destinationCity" to order.destinationCity,
                "truckType" to order.truckType,
                "totalPrice" to order.totalPrice.toString()
            )

            val messageBuilder = RemoteMessage.Builder(fcmToken)
                .setMessageId(orderId)

// Add each entry from the map
            for ((key, value) in notificationData) {
                messageBuilder.addData(key, value)
            }

            FirebaseMessaging.getInstance().send(messageBuilder.build())


        } catch (e: Exception) {
            Log.e(TAG, "Error sending driver notification: ${e.message}")
        }
    }

    /**
     * Notify the customer that a driver has accepted the order
     */
    private suspend fun notifyCustomerOrderAccepted(orderId: String, driverId: String) {
        try {
            // Get the order to find customer UID
            val orderDoc = firestore.collection("orders").document(orderId).get().await()
            val customerUid = orderDoc.getString("uid")

            // Get customer's FCM token
            if (customerUid != null) {
                val customerDoc = firestore.collection("users").document(customerUid).get().await()
                val customerFcmToken = customerDoc.getString("fcmToken")

                if (customerFcmToken != null) {
                    // Get driver details
                    val driverDoc = firestore.collection("users").document(driverId).get().await()
                    val driverName = driverDoc.getString("displayName") ?: "A Driver"

                    // Send notification to customer
                    val data = mapOf(
                        "type" to "order_update",
                        "orderId" to orderId,
                        "status" to "Accepted",
                        "driverName" to driverName
                    )

                    val messageBuilder = RemoteMessage.Builder(customerFcmToken)
                        .setMessageId(orderId)

                    for ((key, value) in data) {
                        messageBuilder.addData(key, value)
                    }

                    FirebaseMessaging.getInstance().send(messageBuilder.build())

                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying customer: ${e.message}")
        }
    }
}

// Companion data model for reference
data class Order(
    val id: String = "",
    val uid: String = "",
    val originCity: String = "",
    val destinationCity: String = "",
    val truckType: String = "",
    val totalPrice: Double = 0.0,
    val status: String = "Pending"
)