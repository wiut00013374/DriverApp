package com.example.driverapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.driverapp.OrderRequestActivity
import com.example.driverapp.R
import com.example.driverapp.data.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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

    @Override
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Message data: ${remoteMessage.data}")

        try {
            // Check if message contains data
            if (remoteMessage.data.isNotEmpty()) {
                val orderId = remoteMessage.data["orderId"]
                val msgType = remoteMessage.data["type"] ?: "unknown"

                Log.d(TAG, "Message type: $msgType, orderId: $orderId")

                when (msgType) {
                    "order_request" -> {
                        if (orderId != null) {
                            Log.d(TAG, "Processing order request for order $orderId")
                            handleOrderRequest(orderId)
                        }
                    }
                    // Handle other message types...
                }
            }

            remoteMessage.notification?.let {
                Log.d(TAG, "Message notification body: ${it.body}")
                // Use title and body from the notification without trying to access an Order
                showBasicNotification(it.title ?: "New Order", it.body ?: "You have a new order request")
            }

            // Handle notification payload if present

        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM message", e)
        }
    }

    private fun showBasicNotification(title: String, message: String) {
        val channelId = "driver_channel"
        val notificationId = System.currentTimeMillis().toInt()

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel for Android 8.0 (Oreo) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Driver Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun showOrderNotification(orderId: String, order: Order) {
        val channelId = "order_requests_channel"
        val channelName = "Order Requests"

        // Create intent for viewing order details
        val intent = Intent(this, OrderRequestActivity::class.java).apply {
            putExtra("order_id", orderId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create accept action intent
        val acceptIntent = Intent(this, OrderActionsReceiver::class.java).apply {
            action = OrderActionsReceiver.ACTION_ACCEPT_ORDER
            putExtra("order_id", orderId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create reject action intent
        val rejectIntent = Intent(this, OrderActionsReceiver::class.java).apply {
            action = OrderActionsReceiver.ACTION_REJECT_ORDER
            putExtra("order_id", orderId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Format price to two decimal places
        val formattedPrice = String.format("$%.2f", order.totalPrice)

        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New Order Request")
            .setContentText("From ${order.originCity} to ${order.destinationCity}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("From: ${order.originCity}\nTo: ${order.destinationCity}\nPrice: $formattedPrice\nTruck: ${order.truckType}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_accept, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_reject, "Reject", rejectPendingIntent)
            .setOngoing(true)  // Make it persistent until user interacts with it

        // Create notification channel for Android O+
        createNotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)

        // Show the notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(orderId.hashCode(), notificationBuilder.build())
    }




    private fun handleOrderRequest(orderId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch order details
                val orderDoc = firestore.collection("orders").document(orderId).get().await()
                val order = orderDoc.toObject(Order::class.java)

                if (order != null) {
                    Log.d(TAG, "Order found: ${order.id}")

                    // Create notification with appropriate intents
                    val intent = Intent(this@DriverFirebaseMessagingService, OrderRequestActivity::class.java).apply {
                        putExtra("order_id", orderId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }

                    val pendingIntent = PendingIntent.getActivity(
                        this@DriverFirebaseMessagingService,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                    )

                    // Create a high-priority notification
                    val notificationBuilder = NotificationCompat.Builder(this@DriverFirebaseMessagingService, "order_requests_channel")
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("New Order Request")
                        .setContentText("From ${order.originCity} to ${order.destinationCity}")
                        .setAutoCancel(true)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)

                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                    // Create channel for Android O and above
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            "order_requests_channel",
                            "Order Requests",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Notifications for new order requests"
                            enableVibration(true)
                            enableLights(true)
                        }
                        notificationManager.createNotificationChannel(channel)
                    }

                    notificationManager.notify(orderId.hashCode(), notificationBuilder.build())

                    // Optionally, show a full screen intent for more visibility
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val fullScreenIntent = Intent(this@DriverFirebaseMessagingService, OrderRequestActivity::class.java).apply {
                            putExtra("order_id", orderId)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }

                        val fullScreenPendingIntent = PendingIntent.getActivity(
                            this@DriverFirebaseMessagingService,
                            orderId.hashCode(),
                            fullScreenIntent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                        )

                        notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
                        notificationManager.notify(orderId.hashCode() + 1, notificationBuilder.build())
                    }
                } else {
                    Log.e(TAG, "Order not found for ID: $orderId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling order request notification", e)
            }
        }
    }

    private fun handleOrderUpdate(orderId: String) {
        // Implementation for order updates
    }

    private fun handleChatMessage(chatId: String) {
        // Implementation for chat messages
    }

    private fun showOrderRequestNotification(orderId: String, order: Order) {
        val channelId = "order_requests_channel"
        val channelName = "Order Requests"

        val intent = Intent(this, OrderRequestActivity::class.java).apply {
            putExtra("order_id", orderId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(this, OrderActionsReceiver::class.java).apply {
            action = OrderActionsReceiver.ACTION_ACCEPT_ORDER
            putExtra("order_id", orderId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            this, 1, acceptIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(this, OrderActionsReceiver::class.java).apply {
            action = OrderActionsReceiver.ACTION_REJECT_ORDER
            putExtra("order_id", orderId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            this, 2, rejectIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedPrice = String.format("$%.2f", order.totalPrice)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New Order Request")
            .setContentText("From ${order.originCity} to ${order.destinationCity}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("From: ${order.originCity}\nTo: ${order.destinationCity}\nPrice: $formattedPrice\nTruck: ${order.truckType}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_accept, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_reject, "Reject", rejectPendingIntent)

        createNotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(orderId.hashCode(), notificationBuilder.build())
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "driver_channel"
        val notificationId = System.currentTimeMillis().toInt()

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel for Android 8.0 (Oreo) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Driver Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun createNotificationChannel(channelId: String, channelName: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for new order requests"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                enableLights(true)
                lightColor = Color.RED
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: $token")

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = firestore.collection("users")
                        .document(user.uid)
                        .update("fcmToken", token)
                        .await()

                    Log.d(TAG, "FCM Token updated in Firestore for user: ${user.uid}")
                } catch (e: FirebaseFirestoreException) {
                    when (e.code) {
                        FirebaseFirestoreException.Code.NOT_FOUND -> {
                            Log.w(TAG, "User document not found, creating new entry.")
                            try {
                                firestore.collection("users")
                                    .document(user.uid)
                                    .set(mapOf("fcmToken" to token))
                                    .await()
                                Log.d(TAG, "New user document created with FCM token.")
                            } catch (e2: Exception) {
                                Log.e(TAG, "Failed to create new user document: ${e2.message}")
                            }
                        }
                        FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                            Log.e(TAG, "Permission denied. Check Firestore rules.")
                        }
                        else -> {
                            Log.e(TAG, "Failed to update token: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error: ${e.message}")
                }
            }
        } else {
            Log.w(TAG, "No authenticated user, cannot update FCM token.")
        }
    }

}