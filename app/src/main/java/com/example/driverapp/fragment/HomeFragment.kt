package com.example.driverapp.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.driverapp.R
import com.example.driverapp.repos.DriverRepository
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    private lateinit var tvTotalTrips: TextView
    private lateinit var tvTotalEarnings: TextView
    private lateinit var tvRating: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        tvTotalTrips = view.findViewById(R.id.tvTotalTrips)
        tvTotalEarnings = view.findViewById(R.id.tvTotalEarnings)
        tvRating = view.findViewById(R.id.tvRating)

        fetchDriverStatistics()

        return view
    }

    private fun fetchDriverStatistics() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val driverId = currentUser?.uid ?: return

        DriverRepository.getDriverStatistics(driverId) { stats ->
            if (stats != null) {
                val totalTrips = stats["totalTrips"] as? Long ?: 0
                val totalEarnings = stats["totalEarnings"] as? Double ?: 0.0
                val rating = stats["rating"] as? Double ?: 0.0

                tvTotalTrips.text = "Total Trips: $totalTrips"
                tvTotalEarnings.text = "Total Earnings: $$totalEarnings"
                tvRating.text = "Rating: $rating"
            }
        }
    }
}