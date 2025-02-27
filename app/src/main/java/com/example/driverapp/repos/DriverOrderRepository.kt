package com.example.driverapp.repos

import android.util.Log
import com.example.driverapp.data.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

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
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for orders: ${error.message}")
                    onOrdersUpdate(emptyList())
                    return@addSnapshotListener
                }

                val orders = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Order::class.java)?.apply {
                            id = doc.id // Ensure the ID is set
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document to Order: ${e.message}")
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "Available orders found: ${orders.size}")
                onOrdersUpdate(orders)
            }
    }

    /**
     * Listen for orders assigned to the current driver
     */
    fun listenForDriverOrders(driverUid: String, onOrdersUpdate: (List<Order>) -> Unit) {
        // Cancel any previous listener
        driverOrdersListenerRegistration?.remove()

        driverOrdersListenerRegistration = firestore.collection(COLLECTION)
            .whereEqualTo("driverUid", driverUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for driver orders: ${error.message}")
                    onOrdersUpdate(emptyList())
                    return@addSnapshotListener
                }

                val orders = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Order::class.java)?.apply {
                            id = doc.id
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting document to Order: ${e.message}")
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "Driver orders found: ${orders.size}")
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
                // Also update the driver's status in the contact list
                updateDriverInContactList(orderId, currentUserId, "accepted")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error accepting order: ${e.message}")
                onComplete(false)
            }
    }

    /**
     * Accept an order - for use with coroutines
     */
    suspend fun acceptOrder(orderId: String): Boolean {
        return try {
            val currentUserId = auth.currentUser?.uid ?: return false

            // First check if the order is still available
            val orderDoc = firestore.collection(COLLECTION).document(orderId).get().await()

            // If order already has a driver, it's too late
            if (orderDoc.getString("driverUid") != null) {
                Log.w(TAG, "Order $orderId already has a driver assigned")
                return false
            }

            // Get the drivers contact list to update it
            val driversContactList = orderDoc.get("driversContactList") as? Map<String, String>

            // Update the order with this driver and change status
            val updates = hashMapOf<String, Any>(
                "driverUid" to currentUserId,
                "status" to "Accepted"
            )

            // Also update the driver's status in the contact list if it exists
            if (driversContactList != null) {
                val updatedList = driversContactList.toMutableMap()
                updatedList[currentUserId] = "accepted"
                updates["driversContactList"] = updatedList
            }

            // Update the order
            firestore.collection(COLLECTION).document(orderId)
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
     * Reject an order - for use with coroutines
     */
    suspend fun rejectOrder(orderId: String): Boolean {
        return try {
            val currentUserId = auth.currentUser?.uid ?: return false

            // Get the order data
            val orderDoc = firestore.collection(COLLECTION).document(orderId).get().await()

            // Get the drivers contact list
            val driversContactList = orderDoc.get("driversContactList") as? Map<String, String>
                ?: return false

            // Update the driver's status in the contact list
            val updatedList = driversContactList.toMutableMap()
            updatedList[currentUserId] = "rejected"

            // Get the current index
            val currentIndex = orderDoc.getLong("currentDriverIndex")?.toInt() ?: 0

            // Update the order with the rejected status and increment the index
            firestore.collection(COLLECTION).document(orderId)
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
                            Log.d(TAG, "Order status updated to $newStatus")
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
                            Log.d(TAG, "Order assignment canceled")
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

    /**
     * Helper function to update a driver's status in the contact list
     */
    private fun updateDriverInContactList(orderId: String, driverId: String, status: String) {
        firestore.collection(COLLECTION).document(orderId)
            .get()
            .addOnSuccessListener { document ->
                val driversContactList = document.get("driversContactList") as? Map<String, String>

                if (driversContactList != null) {
                    // Update the driver's status in the list
                    val updatedList = driversContactList.toMutableMap()
                    updatedList[driverId] = status

                    // Update Firestore
                    firestore.collection(COLLECTION).document(orderId)
                        .update("driversContactList", updatedList)
                        .addOnSuccessListener {
                            Log.d(TAG, "Updated driver $driverId status to $status in contact list")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error updating contact list: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting order for contact list update: ${e.message}")
            }
    }}
