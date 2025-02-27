package com.example.driverapp.repos

import android.util.Log
import com.example.driverapp.data.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * Repository for managing order-related operations from the driver's perspective.
 * This repository uses Firestore to ensure compatibility with the customer app.
 */
object DriverOrderRepository {
    private const val TAG = "DriverOrderRepository"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private const val COLLECTION = "orders"

    private var ordersListenerRegistration: ListenerRegistration? = null
    private var driverOrdersListenerRegistration: ListenerRegistration? = null

    /**
     * Listen for all available pending orders that match the driver's truck type
     */
    fun listenForAvailableOrders(truckType: String, onOrdersUpdate: (List<Order>) -> Unit) {
        // Cancel any previous listener
        ordersListenerRegistration?.remove()

        ordersListenerRegistration = firestore.collection(COLLECTION)
            .whereEqualTo("status", "Pending")
            .whereEqualTo("truckType", truckType)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for orders: ${error.message}")
                    return@addSnapshotListener
                }

                val orders = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Order::class.java)?.apply {
                        id = doc.id // Ensure the ID is set
                    }
                } ?: emptyList()

                onOrdersUpdate(orders)
            }
    }

    /**
     * Listen for orders assigned to the current driver
     */
    fun listenForDriverOrders(onOrdersUpdate: (List<Order>) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return

        // Cancel any previous listener
        driverOrdersListenerRegistration?.remove()

        driverOrdersListenerRegistration = firestore.collection(COLLECTION)
            .whereEqualTo("driverUid", currentUserId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for driver orders: ${error.message}")
                    return@addSnapshotListener
                }

                val orders = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Order::class.java)?.apply {
                        id = doc.id
                    }
                } ?: emptyList()

                onOrdersUpdate(orders)
            }
    }

    /**
     * Accept an order - update the order with the driver's UID and change status
     */
    fun acceptOrder(orderId: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            onComplete(false)
            return
        }

        val updates = mapOf(
            "driverUid" to currentUserId,
            "status" to "Accepted"
        )

        firestore.collection(COLLECTION).document(orderId)
            .update(updates)
            .addOnSuccessListener {
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error accepting order: ${e.message}")
                onComplete(false)
            }
    }

    /**
     * Update order status (In Progress, Delivered, etc.)
     */
    fun updateOrderStatus(orderId: String, newStatus: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid

        // Verify this driver is assigned to the order
        firestore.collection(COLLECTION).document(orderId)
            .get()
            .addOnSuccessListener { document ->
                val order = document.toObject(Order::class.java)
                if (order != null && order.driverUid == currentUserId) {
                    // Driver is authorized to update this order
                    firestore.collection(COLLECTION).document(orderId)
                        .update("status", newStatus)
                        .addOnSuccessListener {
                            onComplete(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error updating order status: ${e.message}")
                            onComplete(false)
                        }
                } else {
                    // Driver is not authorized
                    Log.w(TAG, "Unauthorized attempt to update order")
                    onComplete(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking order: ${e.message}")
                onComplete(false)
            }
    }

    /**
     * Cancel driver assignment from an order
     */
    fun cancelOrderAssignment(orderId: String, onComplete: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid

        // Verify this driver is assigned to the order
        firestore.collection(COLLECTION).document(orderId)
            .get()
            .addOnSuccessListener { document ->
                val order = document.toObject(Order::class.java)
                if (order != null && order.driverUid == currentUserId) {
                    // Driver is authorized to cancel their assignment
                    val updates = mapOf(
                        "driverUid" to null,
                        "status" to "Pending"
                    )

                    firestore.collection(COLLECTION).document(orderId)
                        .update(updates)
                        .addOnSuccessListener {
                            onComplete(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error canceling order assignment: ${e.message}")
                            onComplete(false)
                        }
                } else {
                    // Driver is not authorized
                    Log.w(TAG, "Unauthorized attempt to cancel order")
                    onComplete(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking order: ${e.message}")
                onComplete(false)
            }
    }

    /**
     * Remove all listeners when they're no longer needed
     */
    fun removeListeners() {
        ordersListenerRegistration?.remove()
        driverOrdersListenerRegistration?.remove()
    }
}