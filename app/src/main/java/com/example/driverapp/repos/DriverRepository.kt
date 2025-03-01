package com.example.driverapp.repos

import com.google.firebase.firestore.FirebaseFirestore

object DriverRepository {
    private val firestore = FirebaseFirestore.getInstance()

    fun getDriverStatistics(driverId: String, onComplete: (Map<String, Any>?) -> Unit) {
        firestore.collection("drivers")
            .document(driverId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    onComplete(document.data)
                } else {
                    onComplete(null)
                }
            }
            .addOnFailureListener {
                onComplete(null)
            }
    }
}