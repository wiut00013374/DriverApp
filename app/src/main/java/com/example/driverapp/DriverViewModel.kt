//DriverViewModel.kt
package com.example.driverapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.driverapp.data.Driver
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class DriverViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance() // Realtime Database
    private val auth = FirebaseAuth.getInstance()

    private val _driverData = MutableLiveData<Driver?>()
    val driverData: LiveData<Driver?> = _driverData

    private var driverListener: ValueEventListener? = null // Use ValueEventListener

    init {
        fetchDriverData()
    }

    fun fetchDriverData() {
        val currentUser = auth.currentUser ?: run {
            _driverData.value = null
            return
        }

        val driverId = currentUser.uid
        driverListener = object : ValueEventListener { // Use ValueEventListener
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val driver = snapshot.getValue(Driver::class.java)
                    _driverData.value = driver
                } else {
                    _driverData.value = null
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
                _driverData.value = null
            }
        }

        database.getReference("drivers/$driverId").addValueEventListener(driverListener!!) // Use addValueEventListener
    }

    override fun onCleared() {
        super.onCleared()
        driverListener?.let {
            auth.currentUser?.uid?.let { uid ->
                database.getReference("drivers/$uid").removeEventListener(it)
            }
        } // Remove listener
    }
}