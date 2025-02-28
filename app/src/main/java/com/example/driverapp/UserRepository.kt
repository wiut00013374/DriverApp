package com.example.driverapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.driverapp.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class UserRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var userListener: ListenerRegistration? = null
    private val _userData = MutableLiveData<User?>()
    val userData: LiveData<User?> get() = _userData

    fun fetchUserData() {
        val currentUser = auth.currentUser ?: run {
            _userData.value = null
            return
        }

        val userId = currentUser.uid
        userListener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Handle error
                    _userData.value = null
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    _userData.value = user
                } else {
                    _userData.value = null
                }
            }
    }

    fun removeListener() {
        userListener?.remove()
    }
}
