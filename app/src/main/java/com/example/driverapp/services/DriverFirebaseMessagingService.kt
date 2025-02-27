package com.example.driverapp.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging service for handling push notifications
 */
class DriverFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "DriverFCMService"
    private val firestore = FirebaseFirestore.getInstance()

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val orderId = remoteMessage.data["orderId"]
            val notificationType = remoteMessage.data["type"] ?: "order_request"

            when (notificationType) {
                "order_request" -> {
                    orderId?.let { id ->
                        OrderNotificationHandler.processOrderNotification(this, id)
                    }
                }
                "order_update" -> {
                    orderId?.let { id ->
                        val title = remoteMessage.data["title"] ?: "Order Update"
                        val message = remoteMessage.data["body"] ?: "Your order status has changed"
                        OrderUpdateNotificationHandler.showOrderUpdateNotification(this, id, title, message)
                    }
                }
                "chat_message" -> {
                    val chatId = remoteMessage.data["chatId"] ?: orderId
                    if (chatId != null) {
                        ChatNotificationHandler.processChatNotification(
                            context = this,
                            chatId = chatId,
                            senderId = remoteMessage.data["senderId"] ?: "",
                            message = remoteMessage.data["message"] ?: "New message"
                        )
                    }
                }
            }
        }

        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // If there's a notification but no data, show a generic notification
            if (remoteMessage.data.isEmpty()) {
                GenericNotificationHandler.showBasicNotification(
                    context = this,
                    title = it.title ?: "New Notification",
                    message = it.body ?: "Check your app for details"
                )
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")

        // Update the token in Firestore
        updateTokenInFirestore(token)
    }

    private fun updateTokenInFirestore(token: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update in users collection
                firestore.collection("users").document(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM token updated in users collection")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update FCM token in users collection: ${e.message}")

                        // Try drivers collection as fallback
                        firestore.collection("drivers").document(userId)
                            .update("fcmToken", token)
                            .addOnSuccessListener {
                                Log.d(TAG, "FCM token updated in drivers collection")
                            }
                            .addOnFailureListener { e2 ->
                                Log.e(TAG, "Failed to update FCM token in drivers collection: ${e2.message}")
                            }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating FCM token: ${e.message}")
            }
        }
    }
}