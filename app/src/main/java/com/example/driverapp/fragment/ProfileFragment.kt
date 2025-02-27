package com.example.driverapp.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.driverapp.R
import com.example.driverapp.data.Driver
import com.example.driverapp.login.DriverSignInActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private val TAG = "ProfileFragment"
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // UI elements
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvTruckType: TextView
    private lateinit var tvLicensePlate: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnLogout: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI elements
        tvName = view.findViewById(R.id.textViewFullName)
        tvEmail = view.findViewById(R.id.textViewEmail)
        tvPhone = view.findViewById(R.id.textViewPhoneNumber)
        tvTruckType = view.findViewById(R.id.textViewTruckType)
        tvLicensePlate = view.findViewById(R.id.textViewLicensePlate)
        tvStatus = view.findViewById(R.id.textViewStatus)

        // Add a logout button to the layout
        btnLogout = Button(requireContext()).apply {
            text = "Logout"
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                auth.signOut()
                val intent = Intent(requireContext(), DriverSignInActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
        }

        // Find the LinearLayout in fragment_profile.xml and add the button
        val linearLayout = view.findViewById<ViewGroup>(R.id.linearLayoutProfile)
        linearLayout.addView(btnLogout)

        // Load driver profile data
        loadDriverProfile()
    }

    private fun loadDriverProfile() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // First try to load from "users" collection (this matches the FreightApp structure)
        firestore.collection("users")
            .document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    Log.d(TAG, "User document data: ${document.data}")

                    // Display the user data
                    tvName.text = document.getString("displayName") ?: "Name not set"
                    tvEmail.text = auth.currentUser?.email ?: "Email not available"
                    tvPhone.text = document.getString("phoneNumber") ?: "Phone not set"
                    tvTruckType.text = document.getString("truckType") ?: "Truck type not set"
                    tvLicensePlate.text = document.getString("licensePlate") ?: "License plate not set"
                    tvStatus.text = if (document.getBoolean("available") == true) "Available" else "Not Available"

                    Toast.makeText(requireContext(), "Profile loaded successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "No user document found, checking drivers collection")

                    // If not found in users collection, try the drivers collection
                    firestore.collection("drivers")
                        .document(currentUserId)
                        .get()
                        .addOnSuccessListener { driverDoc ->
                            if (driverDoc != null && driverDoc.exists()) {
                                Log.d(TAG, "Driver document data: ${driverDoc.data}")

                                // Display the driver data
                                tvName.text = driverDoc.getString("displayName") ?: "Name not set"
                                tvEmail.text = auth.currentUser?.email ?: "Email not available"
                                tvPhone.text = driverDoc.getString("phoneNumber") ?: "Phone not set"
                                tvTruckType.text = driverDoc.getString("truckType") ?: "Truck type not set"
                                tvLicensePlate.text = driverDoc.getString("licensePlate") ?: "License plate not set"
                                tvStatus.text = if (driverDoc.getBoolean("available") == true) "Available" else "Not Available"

                                Toast.makeText(requireContext(), "Profile loaded from drivers collection", Toast.LENGTH_SHORT).show()
                            } else {
                                // No profile found in either collection
                                Log.d(TAG, "No profile found in users or drivers collection")
                                tvName.text = "No profile data found"
                                tvEmail.text = auth.currentUser?.email ?: "Email not available"

                                Toast.makeText(requireContext(), "No profile data found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error getting driver document: ${e.message}")
                            Toast.makeText(requireContext(), "Error loading driver profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting user document: ${e.message}")
                Toast.makeText(requireContext(), "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}