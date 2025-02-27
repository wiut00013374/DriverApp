package com.example.driverapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.driverapp.ChatActivity
import com.example.driverapp.MainActivity
import com.example.driverapp.OrderDetailActivity
import com.example.driverapp.R

/**
 * Utility class for handling different types of notifications
 */
class NotificationUtils(private val context: Context) {

    companion object {
        const val CHANNEL_ID_GENERAL = "general_channel"
        const val CHANNEL_ID_ORDERS = "orders_channel"
        const val CHANNEL_ID_CHATS = "chats_channel"
    }

    /**
     * Show a basic notification
     */
    fun showBasicNotification(title: String, message: String, notificationId: Int = System.currentTimeMillis().toInt()) {
        // Create intent for when user taps notification
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)

        // Show notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O+
        createNotificationChannel(
            CHANNEL_ID_GENERAL,
            "General Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Show a notification for order updates
     */
    fun showOrderUpdateNotification(orderId: String, title: String, message: String) {
        // Create intent to open the order details
        val intent = Intent(context, OrderDetailActivity::class.java).apply {
            putExtra("EXTRA_ORDER_ID", orderId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_ORDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)

        // Show notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O+
        createNotificationChannel(
            CHANNEL_ID_ORDERS,
            "Order Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        notificationManager.notify(orderId.hashCode(), notificationBuilder.build())
    }

    /**
     * Show a notification for chat messages
     */
    fun showChatNotification(chatId: String, title: String, message: String) {
        // Create intent to open the chat conversation
        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra("EXTRA_CHAT_ID", chatId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_CHATS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)

        // Show notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O+
        createNotificationChannel(
            CHANNEL_ID_CHATS,
            "Chat Messages",
            NotificationManager.IMPORTANCE_HIGH
        )

        notificationManager.notify(chatId.hashCode(), notificationBuilder.build())
    }

    /**
     * Create a notification channel for Android O and above
     */
    private fun createNotificationChannel(channelId: String, channelName: String, importance: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Channel for $channelName"
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}