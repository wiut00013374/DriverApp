package com.example.driverapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.driverapp.data.Driver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class DriverRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var driverListener: ListenerRegistration? = null
    private val _driverData = MutableLiveData<Driver?>()
    val driverData: LiveData<Driver?> get() = _driverData

    fun fetchDriverData() {
        val currentUser = auth.currentUser ?: run {
            _driverData.value = null
            return
        }

        val driverId = currentUser.uid
        driverListener = firestore.collection("drivers").document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Handle error
                    _driverData.value = null
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val driver = snapshot.toObject(Driver::class.java)
                    _driverData.value = driver
                } else {
                    _driverData.value = null
                }
            }
    }

    fun removeListener() {
        driverListener?.remove()
    }
}
