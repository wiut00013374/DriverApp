package com.example.driverapp.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.driverapp.OrderDetailActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver to handle order accept/reject actions from notifications
 */
class OrderActionsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "OrderActionsReceiver"
        const val ACTION_ACCEPT_ORDER = "com.example.driverapp.ACCEPT_ORDER"
        const val ACTION_REJECT_ORDER = "com.example.driverapp.REJECT_ORDER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val orderId = intent.getStringExtra("order_id") ?: return
        val action = intent.action ?: return

        Log.d(TAG, "Received action: $action for order: $orderId")

        // Cancel the notification
        OrderNotificationHandler.cancelOrderNotification(context, orderId)

        when (action) {
            ACTION_ACCEPT_ORDER -> {
                Toast.makeText(context, "Accepting order...", Toast.LENGTH_SHORT).show()
                handleAcceptOrder(context, orderId)
            }
            ACTION_REJECT_ORDER -> {
                Toast.makeText(context, "Rejecting order...", Toast.LENGTH_SHORT).show()
                handleRejectOrder(context, orderId)
            }
        }
    }

    private fun handleAcceptOrder(context: Context, orderId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = OrderNotificationHandler.acceptOrder(orderId)

                if (success) {
                    // Post the success toast on the main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Order accepted successfully!", Toast.LENGTH_SHORT).show()

                        // Launch the OrderDetailActivity to see the order details
                        val detailsIntent = Intent(context, OrderDetailActivity::class.java).apply {
                            putExtra("EXTRA_ORDER_ID", orderId)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(detailsIntent)
                    }
                } else {
                    // Post the failure toast on the main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Failed to accept order. It may have been taken by another driver.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accepting order: ${e.message}")

                // Post the error toast on the main thread
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleRejectOrder(context: Context, orderId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = OrderNotificationHandler.rejectOrder(orderId)

                // Post the result toast on the main thread
                CoroutineScope(Dispatchers.Main).launch {
                    if (success) {
                        Toast.makeText(context, "Order rejected", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to reject order", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting order: ${e.message}")

                // Post the error toast on the main thread
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}