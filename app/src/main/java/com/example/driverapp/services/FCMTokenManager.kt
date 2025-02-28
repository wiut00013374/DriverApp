package com.example.driverapp.services

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

        FirebaseFirestore.getInstance().collection("users")
            .document(userId)
            .update(tokenData as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "Token successfully updated in Firestore")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error updating token", e)

                // If update fails, try to merge the data
                FirebaseFirestore.getInstance().collection("users")
                    .document(userId)
                    .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Token successfully merged in Firestore")
                    }
                    .addOnFailureListener { mergeError ->
                        Log.e(TAG, "Error merging token", mergeError)
                    }
            }
    }

    /**
     * Firebase Messaging Service to handle token refresh
     */

}