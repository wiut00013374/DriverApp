package com.example.driverapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.driverapp.OrderRequestActivity
import com.example.driverapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Message data: ${remoteMessage.data}")

        try {
            // Check if message contains data
            if (remoteMessage.data.isNotEmpty()) {
                val orderId = remoteMessage.data["orderId"] ?: remoteMessage.data["order_id"]
                val msgType = remoteMessage.data["type"] ?: "unknown"

                Log.d(TAG, "Message type: $msgType, orderId: $orderId")

                when (msgType) {
                    "order_request" -> {
                        if (orderId != null) {
                            Log.d(TAG, "Processing order request for order $orderId")
                            handleOrderRequest(orderId)
                        }
                    }
                    // Handle other message types as needed
                }
            }

            // Handle notification payload if present
            remoteMessage.notification?.let {
                Log.d(TAG, "Message notification body: ${it.body}")
                // Create a basic notification if no data payload
                if (remoteMessage.data.isEmpty()) {
                    showBasicNotification(it.title ?: "New Notification", it.body ?: "Check your app for details")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM message", e)
        }
    }

    private fun handleOrderRequest(orderId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch order details from Firestore
                val orderDoc = firestore.collection("orders").document(orderId).get().await()

                if (orderDoc.exists()) {
                    // Create notification with direct intent to OrderRequestActivity
                    val intent = Intent(this@DriverFirebaseMessagingService, OrderRequestActivity::class.java).apply {
                        putExtra("order_id", orderId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

                    val pendingIntent = PendingIntent.getActivity(
                        this@DriverFirebaseMessagingService,
                        orderId.hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Create accept action
                    val acceptIntent = Intent(this@DriverFirebaseMessagingService, OrderActionsReceiver::class.java).apply {
                        action = OrderActionsReceiver.ACTION_ACCEPT_ORDER
                        putExtra("order_id", orderId)
                    }
                    val acceptPendingIntent = PendingIntent.getBroadcast(
                        this@DriverFirebaseMessagingService,
                        1,
                        acceptIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Create reject action
                    val rejectIntent = Intent(this@DriverFirebaseMessagingService, OrderActionsReceiver::class.java).apply {
                        action = OrderActionsReceiver.ACTION_REJECT_ORDER
                        putExtra("order_id", orderId)
                    }
                    val rejectPendingIntent = PendingIntent.getBroadcast(
                        this@DriverFirebaseMessagingService,
                        2,
                        rejectIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Origin and destination info
                    val originCity = orderDoc.getString("originCity") ?: "Unknown origin"
                    val destinationCity = orderDoc.getString("destinationCity") ?: "Unknown destination"
                    val price = orderDoc.getDouble("totalPrice") ?: 0.0
                    val formattedPrice = String.format("$%.2f", price)

                    // Create notification
                    val channelId = "order_requests_channel"
                    val notificationBuilder = NotificationCompat.Builder(this@DriverFirebaseMessagingService, channelId)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("New Order Request")
                        .setContentText("From $originCity to $destinationCity")
                        .setStyle(NotificationCompat.BigTextStyle()
                            .bigText("From: $originCity\nTo: $destinationCity\nPrice: $formattedPrice"))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .addAction(R.drawable.ic_accept, "Accept", acceptPendingIntent)
                        .addAction(R.drawable.ic_reject, "Reject", rejectPendingIntent)

                    // Create channel for Android O+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            channelId,
                            "Order Requests",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Notifications for new order requests"
                        }
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.createNotificationChannel(channel)
                    }

                    // Show notification
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(orderId.hashCode(), notificationBuilder.build())

                    // Also launch the OrderRequestActivity directly
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling order request: ${e.message}")
            }
        }
    }

    private fun showBasicNotification(title: String, message: String) {
        val channelId = "general_channel"
        val notificationId = System.currentTimeMillis().toInt()

        val intent = Intent(this, OrderRequestActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")

        // Save the new token to Firestore for the current user
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users")
                .document(currentUser.uid)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM token updated successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating FCM token: ${e.message}")
                }
        }
    }
}