// DriverFirebaseMessagingService.kt
package com.example.driverapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.driverapp.OrderRequestActivity
import com.example.driverapp.R
import com.example.driverapp.data.Order
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

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val orderId = remoteMessage.data["orderId"]
            val notificationType = remoteMessage.data["type"] ?: "order_request"

            if (orderId != null) {
                when (notificationType) {
                    "order_request" -> handleOrderRequest(orderId)
                    "order_update" -> handleOrderUpdate(orderId)
                    "chat_message" -> handleChatMessage(orderId)
                }
            }
        }

        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            if (remoteMessage.data.isEmpty()) {
                showBasicNotification(it.title ?: "New Notification", it.body ?: "Check your app for details")
            }
        }
    }

    private fun handleOrderRequest(orderId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val orderDoc = firestore.collection("orders").document(orderId).get().await()
                val order = orderDoc.toObject(Order::class.java)?.apply {
                    id = orderId
                }

                if (order != null) {
                    // Option 1: Launch OrderRequestActivity directly
                    val intent = Intent(this@DriverFirebaseMessagingService, OrderRequestActivity::class.java).apply {
                        putExtra("order_id", orderId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)

                    // Option 2: Show a notification with actions
                    showOrderRequestNotification(orderId, order)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling order request: ${e.message}")
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

    private fun showBasicNotification(title: String, message: String, id: String? = null) {
        // Basic notification implementation
    }

    private fun createNotificationChannel(channelId: String, channelName: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Channel for $channelName"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")

        val user = auth.currentUser
        if (user != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    firestore.collection("users").document(user.uid)
                        .update("fcmToken", token)
                        .await()
                    Log.d(TAG, "FCM Token updated in users collection")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update token: ${e.message}")
                }
            }
        }
    }
}