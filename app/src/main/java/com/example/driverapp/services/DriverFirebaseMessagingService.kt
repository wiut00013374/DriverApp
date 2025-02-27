package com.example.driverapp.services

import android.util.Log
import com.example.driverapp.data.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Service for handling Firebase Cloud Messaging (FCM) notifications
 */
class DriverFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "DriverFCMService"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Get message data
        val data = remoteMessage.data
        Log.d(TAG, "Message data: $data")

        // Process based on notification type
        when (data["type"]) {
            "order_request" -> handleOrderRequest(data)
            "order_update" -> handleOrderUpdate(data)
            "chat_message" -> handleChatMessage(data)
            else -> handleGenericNotification(remoteMessage)
        }
    }

    private fun handleOrderRequest(data: Map<String, String>) {
        val orderId = data["orderId"] ?: return
        Log.d(TAG, "Received order request: $orderId")

        // Check if driver is available
        CoroutineScope(Dispatchers.IO).launch {
            val currentUserId = auth.currentUser?.uid ?: return@launch

            // Check driver availability in Firestore
            try {
                val driverDoc = firestore.collection("users").document(currentUserId).get().await()
                val isAvailable = driverDoc.getBoolean("available") ?: false

                if (!isAvailable) {
                    Log.d(TAG, "Driver is not available, ignoring order request")
                    return@launch
                }

                // Process the order request notification
                processOrderRequest(orderId)

            } catch (e: Exception) {
                Log.e(TAG, "Error checking driver availability: ${e.message}")
            }
        }
    }

    private fun processOrderRequest(orderId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get the order details
                val orderDoc = firestore.collection("orders").document(orderId).get().await()
                val order = orderDoc.toObject(Order::class.java)?.apply {
                    id = orderId
                }

                if (order != null) {
                    // Show notification with the order details
                    CoroutineScope(Dispatchers.Main).launch {
                        OrderNotificationHandler.showOrderRequestNotification(applicationContext, order)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing order request: ${e.message}")
            }
        }
    }

    private fun handleOrderUpdate(data: Map<String, String>) {
        val orderId = data["orderId"] ?: return
        val status = data["status"] ?: "updated"

        Log.d(TAG, "Received order update: $orderId, status: $status")

        // Show a notification about the order update
        val title = "Order Update"
        val message = when (status) {
            "cancelled" -> "Order has been cancelled by the customer"
            "completed" -> "Order has been marked as completed"
            else -> "Order status has been updated to $status"
        }

        NotificationUtils(applicationContext).showBasicNotification(title, message, orderId.hashCode())
    }

    private fun handleChatMessage(data: Map<String, String>) {
        val chatId = data["chatId"] ?: return
        val senderId = data["senderId"] ?: return
        val messageText = data["message"] ?: "New message"

        Log.d(TAG, "Received chat message: $chatId from $senderId")

        // Check if sender is not the current user
        val currentUserId = auth.currentUser?.uid
        if (senderId == currentUserId) {
            return // Don't show notifications for own messages
        }

        // Fetch sender info if available
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val senderName = if (senderId.isNotEmpty()) {
                    val senderDoc = firestore.collection("users").document(senderId).get().await()
                    senderDoc.getString("displayName") ?: "Customer"
                } else "Customer"

                // Show chat notification
                val title = "Message from $senderName"
                NotificationUtils(applicationContext).showChatNotification(chatId, title, messageText)

            } catch (e: Exception) {
                Log.e(TAG, "Error handling chat message: ${e.message}")

                // Fallback to generic notification if sender info fails
                NotificationUtils(applicationContext).showChatNotification(chatId, "New Message", messageText)
            }
        }
    }

    private fun handleGenericNotification(remoteMessage: RemoteMessage) {
        val notification = remoteMessage.notification

        if (notification != null) {
            val title = notification.title ?: "New Notification"
            val body = notification.body ?: "You have a new notification"

            NotificationUtils(applicationContext).showBasicNotification(title, body, System.currentTimeMillis().toInt())
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")

        // Save the new token to Firestore
        updateTokenInFirestore(token)
    }

    private fun updateTokenInFirestore(token: String) {
        val user = auth.currentUser ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update in users collection
                firestore.collection("users").document(user.uid)
                    .update("fcmToken", token)
                    .await()
                Log.d(TAG, "FCM Token updated in users collection")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update token in users collection: ${e.message}")

                // If update fails, try to use set with merge option
                try {
                    firestore.collection("users").document(user.uid)
                        .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                        .await()
                    Log.d(TAG, "FCM Token set in users collection using merge")
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to set token in users collection: ${e2.message}")
                }
            }
        }
    }
}