package com.example.driverapp.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object FCMTokenManager {
    const val TAG = "FCMTokenManager"

    /**
     * Retrieve and save the current FCM token
     */
    fun retrieveAndSaveToken() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            // Log and send to your server/Firestore
            Log.d(TAG, "FCM Token: $token")
            saveTokenToFirestore(currentUser.uid, token)
        }
    }

    /**
     * Save the FCM token to Firestore
     */
    fun saveTokenToFirestore(userId: String, token: String) {
        val tokenData = hashMapOf(
            "fcmToken" to token,
            "tokenUpdatedAt" to System.currentTimeMillis()
        )

        val firestore = FirebaseFirestore.getInstance()

        // Try to update the token in the users collection
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // First try to update the token (if the document exists)
                firestore.collection("users")
                    .document(userId)
                    .update("fcmToken", token)
                    .await()

                Log.d(TAG, "Token successfully updated in users collection")
            } catch (e: Exception) {
                // If update fails (because document might not exist), try to merge
                Log.w(TAG, "Update failed, trying merge: ${e.message}")

                try {
                    firestore.collection("users")
                        .document(userId)
                        .set(tokenData, SetOptions.merge())
                        .await()

                    Log.d(TAG, "Token successfully merged in users collection")
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to save token to Firestore: ${e2.message}")
                }
            }
        }
    }
}