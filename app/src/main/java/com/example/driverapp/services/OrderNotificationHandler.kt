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
import com.example.driverapp.MainActivity
import com.example.driverapp.OrderDetailActivity
import com.example.driverapp.R
import com.example.driverapp.data.Order
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Utility class to handle different types of notifications for drivers
 */
object OrderNotificationHandler {
    private const val TAG = "OrderNotificationHandler"
    private const val CHANNEL_ID_ORDER_REQUESTS = "order_requests_channel"
    private const val NOTIFICATION_ID_PREFIX = 1000 // Offset for order notification IDs

    /**
     * Show a notification for a new order request
     */
    fun showOrderRequestNotification(context: Context, order: Order) {
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
     * Cancel a notification for an order
     */
    fun cancelOrderNotification(context: Context, orderId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NOTIFICATION_ID_PREFIX + orderId.hashCode()
        notificationManager.cancel(notificationId)
    }
}

