package com.example.driverapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Utility class for sending and handling notifications
 */
object NotificationService {
    private const val TAG = "NotificationService"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Channel IDs
    private const val DRIVER_CHANNEL_ID = "driver_notifications"
    private const val CUSTOMER_CHANNEL_ID = "customer_notifications"

    /**
     * Send a notification to the current driver
     */
    fun sendDriverNotification(context: Context, title: String, message: String) {
        // Create notification channel for Android O and above
        createDriverNotificationChannel(context)

        // Create and show notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = androidx.core.app.NotificationCompat.Builder(context, DRIVER_CHANNEL_ID)
            .setSmallIcon(com.example.driverapp.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Send an order update notification to the customer via FCM
     */
    fun sendCustomerOrderUpdate(context: Context, customerUid: String, orderId: String, title: String, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get the customer's FCM token
                val customerDoc = firestore.collection("users")
                    .document(customerUid)
                    .get()
                    .await()

                val fcmToken = customerDoc.getString("fcmToken")
                if (fcmToken.isNullOrEmpty()) {
                    Log.e(TAG, "Customer FCM token not found")
                    return@launch
                }

                // Get the driver's information for the notification
                val driverUid = auth.currentUser?.uid
                var driverName = "Driver"

                if (driverUid != null) {
                    val driverDoc = firestore.collection("users")
                        .document(driverUid)
                        .get()
                        .await()

                    driverName = driverDoc.getString("displayName")
                        ?: driverDoc.getString("fullName")
                                ?: auth.currentUser?.displayName
                                ?: "Driver"
                }

                // Create notification data
                val data = mapOf(
                    "type" to "order_update",
                    "orderId" to orderId,
                    "title" to title,
                    "message" to message,
                    "driverName" to driverName,
                    "timestamp" to System.currentTimeMillis().toString()
                )

                // Send the FCM message
                val message = RemoteMessage.Builder(fcmToken)
                    .setMessageId(orderId + "_" + System.currentTimeMillis())
                    .setData(data)
                    .build()

                FirebaseMessaging.getInstance().send(message)

                Log.d(TAG, "Notification sent to customer $customerUid: $title")

                // Also update the order with a notification record
                val notificationData = hashMapOf(
                    "type" to "order_update",
                    "title" to title,
                    "message" to message,
                    "timestamp" to System.currentTimeMillis(),
                    "read" to false
                )

                firestore.collection("orders")
                    .document(orderId)
                    .collection("notifications")
                    .add(notificationData)
                    .await()

                // Update order with latest status message
                firestore.collection("orders")
                    .document(orderId)
                    .update(
                        mapOf(
                            "lastStatusUpdate" to System.currentTimeMillis(),
                            "lastStatusMessage" to message
                        )
                    )
                    .await()

            } catch (e: Exception) {
                Log.e(TAG, "Error sending customer notification: ${e.message}")
            }
        }
    }

    /**
     * Create notification channel for driver notifications
     */
    private fun createDriverNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Driver Notifications"
            val descriptionText = "Notifications for drivers"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(DRIVER_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification channel for customer notifications
     */
    fun createCustomerNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Customer Notifications"
            val descriptionText = "Notifications for customers"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CUSTOMER_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}