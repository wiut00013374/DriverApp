package com.example.driverapp.services

import android.util.Log
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
                // In DriverFirebaseMessagingService:
                OrderNotificationHandler.processOrderNotification(applicationContext, orderId)


            } catch (e: Exception) {
                Log.e(TAG, "Error checking driver availability: ${e.message}")
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

        showBasicNotification(title, message, orderId.hashCode())
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
                showChatNotification(chatId, title, messageText)

            } catch (e: Exception) {
                Log.e(TAG, "Error handling chat message: ${e.message}")

                // Fallback to generic notification if sender info fails
                showChatNotification(chatId, "New Message", messageText)
            }
        }
    }

    private fun handleGenericNotification(remoteMessage: RemoteMessage) {
        val notification = remoteMessage.notification

        if (notification != null) {
            val title = notification.title ?: "New Notification"
            val body = notification.body ?: "You have a new notification"

            showBasicNotification(title, body, System.currentTimeMillis().toInt())
        }
    }

    private fun showBasicNotification(title: String, message: String, notificationId: Int) {
        val notificationUtils = NotificationUtils(applicationContext)
        notificationUtils.showBasicNotification(title, message, notificationId)
    }

    private fun showChatNotification(chatId: String, title: String, message: String) {
        val notificationUtils = NotificationUtils(applicationContext)
        notificationUtils.showChatNotification(chatId, title, message)
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
                // Try updating in both collections to ensure we cover all bases
                try {
                    firestore.collection("users").document(user.uid)
                        .update("fcmToken", token)
                        .await()
                    Log.d(TAG, "FCM Token updated in users collection")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update token in users collection: ${e.message}")
                }

                try {
                    firestore.collection("drivers").document(user.uid)
                        .update("fcmToken", token)
                        .await()
                    Log.d(TAG, "FCM Token updated in drivers collection")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update token in drivers collection: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating FCM token: ${e.message}")
            }
        }
    }
}