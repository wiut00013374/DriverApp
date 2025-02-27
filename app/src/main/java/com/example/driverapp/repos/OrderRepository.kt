package com.example.driverapp.repos

import com.example.driverapp.data.Order
import com.google.firebase.firestore.FirebaseFirestore

object OrderRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private const val COLLECTION = "orders"

    /**
     * Listen for all orders that are still pending (status = "Pending").
     * This gives the driver a real-time feed of unassigned orders.
     */
    fun listenForPendingOrders(truckType: String, onOrdersUpdate: (List<Order>) -> Unit) {
        firestore.collection(COLLECTION)
            .whereEqualTo("status", "Pending")
            .whereEqualTo("truckType", truckType)  // filter by truckType
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    // Handle/log error if needed
                    return@addSnapshotListener
                }
                val orders = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(Order::class.java)?.apply {
                        id = doc.id // Firestore doc ID
                    }
                } ?: emptyList()
                onOrdersUpdate(orders)
            }
    }

    /**
     * Assign the current driver to the order, setting status to "Accepted".
     */
    fun assignDriverToOrder(orderId: String, driverUid: String, onComplete: (Boolean) -> Unit) {
        val updates = mapOf(
            "driverUid" to driverUid,
            "status" to "Accepted"
        )
        firestore.collection(COLLECTION).document(orderId)
            .update(updates)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }
}
