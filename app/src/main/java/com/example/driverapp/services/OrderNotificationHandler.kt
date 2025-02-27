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
import com.example.driverapp.OrderDetailActivity
import com.example.driverapp.R
import com.example.driverapp.data.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Handles order notifications for drivers and provides methods to accept/reject orders
 */
object OrderNotificationHandler {
    private const val TAG = "OrderNotificationHandler"
    private const val CHANNEL_ID_ORDER_REQUESTS = "order_requests_channel"
    private const val NOTIFICATION_ID_PREFIX = 1000 // Offset for order notification IDs

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Process an incoming order notification
     */
    fun processOrderNotification(context: Context, orderId: String, showNotification: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if driver is still available
                val currentUserId = auth.currentUser?.uid
                if (currentUserId == null) {
                    Log.e(TAG, "No authenticated user")
                    return@launch
                }

                val driverDoc = firestore.collection("users").document(currentUserId).get().await()
                val isAvailable = driverDoc.getBoolean("available") ?: false

                if (!isAvailable) {
                    Log.d(TAG, "Driver is not available, skipping notification")

                    // Update order's driver contact list to indicate unavailability
                    val orderDoc = firestore.collection("orders").document(orderId).get().await()
                    @Suppress("UNCHECKED_CAST")
                    val driversContactList = orderDoc.get("driversContactList") as? MutableMap<String, String>

                    if (driversContactList != null && driversContactList.containsKey(currentUserId)) {
                        driversContactList[currentUserId] = "unavailable"

                        // Update the order
                        firestore.collection("orders").document(orderId)
                            .update("driversContactList", driversContactList)
                            .await()
                    }

                    return@launch
                }

                // Get the order details
                val orderDoc = firestore.collection("orders").document(orderId).get().await()
                val order = orderDoc.toObject(Order::class.java)?.apply {
                    id = orderDoc.id
                }

                if (order == null) {
                    Log.e(TAG, "Order $orderId not found")
                    return@launch
                }

                // Cache the order for quick access when viewing details
                cacheOrderLocally(context, order)

                // Show notification if requested
                if (showNotification) {
                    CoroutineScope(Dispatchers.Main).launch {
                        showOrderRequestNotification(context, order)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing order notification: ${e.message}")
            }
        }
    }

    /**
     * Show a notification for a new order request
     */
    private fun showOrderRequestNotification(context: Context, order: Order) {
        // Create notification channel for Android O and above
        createOrderRequestChannel(context)

        // Create intent for viewing order details
        val detailsIntent = Intent(context, OrderDetailActivity::class.java).apply {
            putExtra("EXTRA_ORDER", order)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val detailsPendingIntent = PendingIntent.getActivity(
            context, 0, detailsIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for accepting order
        val acceptIntent = Intent(context, OrderActionsReceiver::class.java).apply {
            action = OrderActionsReceiver.ACTION_ACCEPT_ORDER
            putExtra("order_id", order.id)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            context, 1, acceptIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for rejecting order
        val rejectIntent = Intent(context, OrderActionsReceiver::class.java).apply {
            action = OrderActionsReceiver.ACTION_REJECT_ORDER
            putExtra("order_id", order.id)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context, 2, rejectIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Format price to two decimal places
        val formattedPrice = String.format("$%.2f", order.totalPrice)

        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_ORDER_REQUESTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New Order Request")
            .setContentText("From: ${order.originCity} to: ${order.destinationCity}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("From: ${order.originCity}\nTo: ${order.destinationCity}\nPrice: $formattedPrice\nTruck: ${order.truckType}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setAutoCancel(true)
            .setContentIntent(detailsPendingIntent)
            .addAction(R.drawable.ic_notification, "View Details", detailsPendingIntent)
            .addAction(R.drawable.ic_notification, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_notification, "Reject", rejectPendingIntent)

        // Show the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NOTIFICATION_ID_PREFIX + order.id.hashCode()
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "Showed notification for order ${order.id}")
    }

    /**
     * Create notification channel for order requests (Android O and above)
     */
    private fun createOrderRequestChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ORDER_REQUESTS,
                "Order Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new order requests"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Cache the order details locally for quick access
     */
    private fun cacheOrderLocally(context: Context, order: Order) {
        // Use SharedPreferences for simple caching
        val sharedPrefs = context.getSharedPreferences("order_cache", Context.MODE_PRIVATE)

        with(sharedPrefs.edit()) {
            // Store order as JSON string (would be better with a proper serialization library)
            putString("order_${order.id}", "{" +
                    "\"id\":\"${order.id}\"," +
                    "\"uid\":\"${order.uid}\"," +
                    "\"originCity\":\"${order.originCity}\"," +
                    "\"destinationCity\":\"${order.destinationCity}\"," +
                    "\"originLat\":${order.originLat}," +
                    "\"originLon\":${order.originLon}," +
                    "\"destinationLat\":${order.destinationLat}," +
                    "\"destinationLon\":${order.destinationLon}," +
                    "\"totalPrice\":${order.totalPrice}," +
                    "\"truckType\":\"${order.truckType}\"," +
                    "\"volume\":${order.volume}," +
                    "\"weight\":${order.weight}," +
                    "\"status\":\"${order.status}\"" +
                    "}")
            apply()
        }
    }

    /**
     * Accept an order - makes request to update in Firestore
     */
    suspend fun acceptOrder(orderId: String): Boolean {
        return try {
            // Get current driver ID
            val currentUserId = auth.currentUser?.uid ?: return false

            // First check if the order is still available
            val orderDoc = firestore.collection("orders").document(orderId).get().await()

            // If order already has a driver, it's too late
            if (orderDoc.getString("driverUid") != null) {
                Log.w(TAG, "Order $orderId already has a driver assigned")
                return false
            }

            // Get the drivers contact list
            @Suppress("UNCHECKED_CAST")
            val driversContactList = orderDoc.get("driversContactList") as? Map<String, String>

            if (driversContactList == null || !driversContactList.containsKey(currentUserId)) {
                Log.e(TAG, "Driver not in contact list for order $orderId")
                return false
            }

            // Update the order with this driver and change status
            val updates = hashMapOf<String, Any>(
                "driverUid" to currentUserId,
                "status" to "Accepted",
                "acceptedAt" to System.currentTimeMillis()
            )

            // Also update the driver's status in the contact list
            val updatedList = driversContactList.toMutableMap()
            updatedList[currentUserId] = "accepted"
            updates["driversContactList"] = updatedList

            // Update the order
            firestore.collection("orders").document(orderId)
                .update(updates)
                .await()

            Log.d(TAG, "Order $orderId accepted by driver $currentUserId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting order: ${e.message}")
            return false
        }
    }

    /**
     * Reject an order - makes request to update in Firestore
     */
    suspend fun rejectOrder(orderId: String): Boolean {
        return try {
            // Get current driver ID
            val currentUserId = auth.currentUser?.uid ?: return false

            // Get the order data
            val orderDoc = firestore.collection("orders").document(orderId).get().await()

            // Get the drivers contact list
            @Suppress("UNCHECKED_CAST")
            val driversContactList = orderDoc.get("driversContactList") as? Map<String, String>
                ?: return false

            // Update the driver's status in the contact list
            val updatedList = driversContactList.toMutableMap()
            updatedList[currentUserId] = "rejected"

            // Get the current index
            val currentIndex = orderDoc.getLong("currentDriverIndex")?.toInt() ?: 0

            // Update the order with the rejected status and increment the index
            firestore.collection("orders").document(orderId)
                .update(
                    mapOf(
                        "driversContactList" to updatedList,
                        "currentDriverIndex" to currentIndex + 1
                    )
                )
                .await()

            Log.d(TAG, "Order $orderId rejected by driver $currentUserId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting order: ${e.message}")
            return false
        }
    }

    /**
     * Cancel a notification for an order
     */
    fun cancelOrderNotification(context: Context, orderId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NOTIFICATION_ID_PREFIX + orderId.hashCode()
        notificationManager.cancel(notificationId)
    }
}