package com.example.driverapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.driverapp.OrderDetailActivity
import com.example.driverapp.R
import com.example.driverapp.data.Order
import com.example.driverapp.services.FCMTokenManager.saveTokenToFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DriverFirebaseMessagingService : FirebaseMessagingService() {

    private val TAG = "DriverFCMService"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        fun retrieveAndSaveToken() {
            val currentUser = FirebaseAuth.getInstance().currentUser ?: return

            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(FCMTokenManager.TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result

                // Log and send to your server/Firestore
                Log.d(FCMTokenManager.TAG, "FCM Token: $token")
                saveTokenToFirestore(currentUser.uid, token)
            }
        }

        private const val CHANNEL_ID_ORDER_REQUESTS = "order_request_channel"
        private const val ACTION_ACCEPT_ORDER = "com.example.driverapp.ACCEPT_ORDER"
        private const val ACTION_REJECT_ORDER = "com.example.driverapp.REJECT_ORDER"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Order Requests"
            val descriptionText = "Notifications for new order requests"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                CHANNEL_ID_ORDER_REQUESTS,
                name,
                importance
            ).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

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

        CoroutineScope(Dispatchers.IO).launch {
            val currentUserId = auth.currentUser?.uid ?: return@launch

            try {
                // Check driver availability in Firestore
                val driverDoc = firestore.collection("users").document(currentUserId).get().await()
                val isAvailable = driverDoc.getBoolean("available") ?: false

                if (!isAvailable) {
                    Log.d(TAG, "Driver is not available, ignoring order request")
                    return@launch
                }

                // Fetch order details
                val orderDoc = firestore.collection("orders").document(orderId).get().await()
                val orderData = orderDoc.data ?: return@launch

                // Create accept and reject intents
                val acceptIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    0,
                    Intent(ACTION_ACCEPT_ORDER).putExtra("order_id", orderId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val rejectIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    1,
                    Intent(ACTION_REJECT_ORDER).putExtra("order_id", orderId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Build the notification
                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_ORDER_REQUESTS)
                    .setContentTitle("New Order Request")
                    .setContentText("From ${orderData["originCity"]} to ${orderData["destinationCity"]}")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSmallIcon(R.drawable.ic_notification)
                    .addAction(R.drawable.ic_accept, "Accept", acceptIntent)
                    .addAction(R.drawable.ic_reject, "Reject", rejectIntent)
                    .setAutoCancel(true)
                    .build()

                // Check if POST_NOTIFICATIONS permission is granted (required on Android 13+)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        applicationContext,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(applicationContext)
                        .notify(orderId.hashCode(), notification)
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted; cannot show order request notification.")
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

        // Build the notification
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_ORDER_REQUESTS)
            .setContentTitle("Order Update")
            .setContentText("Order status: $status")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()

        // Check POST_NOTIFICATIONS permission on Android 13+ before notifying
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(applicationContext)
                .notify(orderId.hashCode(), notification)
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted; cannot show notification.")
        }
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

        // Build the chat message notification
        val chatNotification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_ORDER_REQUESTS)
            .setContentTitle("New Message")
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()

        // Check POST_NOTIFICATIONS permission on Android 13+ before notifying
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is granted, so we can show the notification
            NotificationManagerCompat.from(applicationContext)
                .notify(chatId.hashCode(), chatNotification)
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted; cannot show notification.")
        }
    }


    private fun handleGenericNotification(remoteMessage: RemoteMessage) {
        val notification = remoteMessage.notification ?: return
        val title = notification.title ?: "New Notification"
        val body = notification.body ?: "You have a new notification"

        // Build the notification
        val genericNotification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_ORDER_REQUESTS)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()

        // Check if POST_NOTIFICATIONS permission is needed and granted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Safe to post the notification
            NotificationManagerCompat.from(applicationContext)
                .notify(System.currentTimeMillis().toInt(), genericNotification)
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted; cannot show notification.")
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