package com.example.driverapp.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.driverapp.OrderDetailActivity
import com.example.driverapp.R
import com.example.driverapp.data.Order
import com.example.driverapp.utils.DriverAvailabilityManager
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {
    private val TAG = "HomeFragment"

    private lateinit var tvWelcomeMessage: TextView
    private lateinit var switchAvailability: Switch
    private lateinit var tvAvailabilityStatus: TextView
    private lateinit var tvTotalTrips: TextView
    private lateinit var tvTotalEarnings: TextView
    private lateinit var tvRating: TextView

    // Recent orders section
    private lateinit var cardRecentOrders: MaterialCardView
    private lateinit var tvLatestOrderOrigin: TextView
    private lateinit var tvLatestOrderDestination: TextView
    private lateinit var tvLatestOrderStatus: TextView
    private lateinit var tvLatestOrderTime: TextView
    private lateinit var btnViewLatestOrder: Button

    // Earnings summary
    private lateinit var tvTodayEarnings: TextView
    private lateinit var tvWeekEarnings: TextView
    private lateinit var tvMonthEarnings: TextView

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var latestOrderId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        initializeViews(view)
        setupAvailabilitySwitch()

        // Show loading state initially
        showLoadingState()

        // Load all data
        loadAllData()

        return view
    }

    private fun showLoadingState() {
        // Set placeholder text while data loads
        tvWelcomeMessage.text = "Loading..."
        tvTotalTrips.text = "Total Trips: -"
        tvTotalEarnings.text = "Total Earnings: $-"
        tvRating.text = "Rating: - ★"
        tvTodayEarnings.text = "Today: $-"
        tvWeekEarnings.text = "This Week: $-"
        tvMonthEarnings.text = "This Month: $-"
        cardRecentOrders.visibility = View.GONE
    }

    private fun loadAllData() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                loadDriverData()
                loadLatestOrder()
                loadEarningsSummary()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data: ${e.message}")
                Toast.makeText(context, "Error loading dashboard data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeViews(view: View) {
        // Initialize all view references
        tvWelcomeMessage = view.findViewById(R.id.tvWelcomeMessage)
        switchAvailability = view.findViewById(R.id.switchAvailability)
        tvAvailabilityStatus = view.findViewById(R.id.tvAvailabilityStatus)
        tvTotalTrips = view.findViewById(R.id.tvTotalTrips)
        tvTotalEarnings = view.findViewById(R.id.tvTotalEarnings)
        tvRating = view.findViewById(R.id.tvRating)

        // Recent order card
        cardRecentOrders = view.findViewById(R.id.cardRecentOrders)
        tvLatestOrderOrigin = view.findViewById(R.id.tvLatestOrderOrigin)
        tvLatestOrderDestination = view.findViewById(R.id.tvLatestOrderDestination)
        tvLatestOrderStatus = view.findViewById(R.id.tvLatestOrderStatus)
        tvLatestOrderTime = view.findViewById(R.id.tvLatestOrderTime)
        btnViewLatestOrder = view.findViewById(R.id.btnViewLatestOrder)

        // Earnings summary
        tvTodayEarnings = view.findViewById(R.id.tvTodayEarnings)
        tvWeekEarnings = view.findViewById(R.id.tvWeekEarnings)
        tvMonthEarnings = view.findViewById(R.id.tvMonthEarnings)

        // Set up button click listener for latest order
        btnViewLatestOrder.setOnClickListener {
            latestOrderId?.let { id ->
                val intent = Intent(requireContext(), OrderDetailActivity::class.java)
                intent.putExtra("EXTRA_ORDER_ID", id)
                startActivity(intent)
            }
        }
    }

    private fun setupAvailabilitySwitch() {
        // Check current availability status
        val currentUserId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                val available = document.getBoolean("available") ?: false
                switchAvailability.isChecked = available
                updateAvailabilityUI(available)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting availability status: ${e.message}")
                switchAvailability.isChecked = false
                updateAvailabilityUI(false)
            }

        // Set up switch listener
        switchAvailability.setOnCheckedChangeListener { _, isChecked ->
            switchAvailability.isEnabled = false // Disable until operation completes

            // Update UI immediately for responsive feel
            updateAvailabilityUI(isChecked)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Check if context is still valid
                    val context = context ?: return@launch

                    // Call static method from DriverAvailabilityManager
                    updateDriverAvailabilityInFirestore(isChecked)

                    withContext(Dispatchers.Main) {
                        switchAvailability.isEnabled = true
                        Toast.makeText(context,
                            if (isChecked) "You are now available for orders"
                            else "You are now offline",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating availability: ${e.message}")
                    withContext(Dispatchers.Main) {
                        switchAvailability.isEnabled = true
                        switchAvailability.isChecked = !isChecked
                        updateAvailabilityUI(!isChecked)

                        context?.let {
                            Toast.makeText(it, "Error updating status: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private suspend fun updateDriverAvailabilityInFirestore(isAvailable: Boolean): Boolean {
        val currentUserId = auth.currentUser?.uid ?: return false

        return try {
            // Start or stop location service based on availability
            if (context != null) {
                if (isAvailable) {
                    startLocationService()
                } else {
                    stopLocationService()
                }
            }

            // Update availability in Firestore
            val updates = mapOf(
                "available" to isAvailable,
                "lastStatusUpdate" to System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(currentUserId)
                .update(updates)
                .await()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating driver availability: ${e.message}")
            false
        }
    }

    private fun startLocationService() {
        context?.let { ctx ->
            val serviceIntent = Intent("com.example.driverapp.START_LOCATION_SERVICE")
            serviceIntent.setPackage(ctx.packageName)
            ctx.startService(serviceIntent)
        }
    }

    private fun stopLocationService() {
        context?.let { ctx ->
            val serviceIntent = Intent("com.example.driverapp.STOP_LOCATION_SERVICE")
            serviceIntent.setPackage(ctx.packageName)
            ctx.startService(serviceIntent)
        }
    }

    private fun updateAvailabilityUI(available: Boolean) {
        val context = context ?: return

        tvAvailabilityStatus.text = if (available) {
            "You are available for orders"
        } else {
            "You are not available for orders"
        }

        // Change text color based on availability
        tvAvailabilityStatus.setTextColor(
            if (available) ContextCompat.getColor(context, R.color.teal_700)
            else ContextCompat.getColor(context, android.R.color.darker_gray)
        )
    }

    private suspend fun loadDriverData() {
        val currentUserId = auth.currentUser?.uid ?: return

        try {
            // Get driver profile from Firestore
            val document = withContext(Dispatchers.IO) {
                firestore.collection("users").document(currentUserId).get().await()
            }

            if (document.exists()) {
                // Get driver's name for welcome message
                val driverName = document.getString("displayName")
                    ?: document.getString("driverName")
                    ?: auth.currentUser?.displayName
                    ?: "Driver"

                tvWelcomeMessage.text = "Welcome, $driverName!"

                // Get statistics - first check for specific fields
                val completedOrders = document.getLong("completedOrders") ?: 0L

                // Calculate total earnings and trips if they're not already stored
                var totalEarnings = document.getDouble("totalEarnings")
                var totalTrips = document.getLong("totalTrips")

                // If these values aren't stored in the user profile, calculate them
                if (totalEarnings == null || totalTrips == null) {
                    val result = calculateLifetimeStats(currentUserId)
                    totalEarnings = result.first
                    totalTrips = result.second.toLong()

                    // Update the user's profile with these calculated values for future use
                    withContext(Dispatchers.IO) {
                        firestore.collection("users").document(currentUserId)
                            .update(
                                mapOf(
                                    "totalEarnings" to totalEarnings,
                                    "totalTrips" to totalTrips
                                )
                            )
                    }
                }

                // Get rating with fallback value
                val rating = document.getDouble("rating") ?: 5.0

                // Update UI
                tvTotalTrips.text = "Total Trips: $totalTrips"
                tvTotalEarnings.text = "Total Earnings: ${String.format("%.2f", totalEarnings)}"
                tvRating.text = "Rating: ${String.format("%.1f", rating)} ★"
            } else {
                Log.d(TAG, "No driver profile found")
                tvWelcomeMessage.text = "Welcome to Driver App!"
                tvTotalTrips.text = "Total Trips: 0"
                tvTotalEarnings.text = "Total Earnings: $0.00"
                tvRating.text = "Rating: 5.0 ★"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading driver data: ${e.message}")
            withContext(Dispatchers.Main) {
                tvWelcomeMessage.text = "Welcome, Driver!"
                tvTotalTrips.text = "Total Trips: --"
                tvTotalEarnings.text = "Total Earnings: --"
                tvRating.text = "Rating: --"
            }
        }
    }

    private suspend fun calculateLifetimeStats(driverId: String): Pair<Double, Int> {
        var totalEarnings = 0.0
        var totalTrips = 0

        try {
            // Get all completed/delivered orders for this driver
            val ordersSnapshot = withContext(Dispatchers.IO) {
                firestore.collection("orders")
                    .whereEqualTo("driverUid", driverId)
                    .whereIn("status", listOf("Delivered", "Completed"))
                    .get()
                    .await()
            }

            totalTrips = ordersSnapshot.size()

            // Sum up the earnings
            for (doc in ordersSnapshot.documents) {
                val price = doc.getDouble("totalPrice") ?: 0.0
                totalEarnings += price
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating lifetime stats: ${e.message}")
        }

        return Pair(totalEarnings, totalTrips)
    }

    private suspend fun loadLatestOrder() {
        val currentUserId = auth.currentUser?.uid ?: return

        try {
            // Query orders assigned to this driver
            val snapshot = withContext(Dispatchers.IO) {
                firestore.collection("orders")
                    .whereEqualTo("driverUid", currentUserId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()
            }

            withContext(Dispatchers.Main) {
                if (snapshot.isEmpty) {
                    cardRecentOrders.visibility = View.GONE
                    return@withContext
                }

                cardRecentOrders.visibility = View.VISIBLE
                val document = snapshot.documents[0]

                // Get order ID for future navigation
                latestOrderId = document.id

                // Try to convert to Order object
                val latestOrder = document.toObject(Order::class.java)

                if (latestOrder != null) {
                    // Ensure the order has the correct ID
                    latestOrder.id = document.id

                    // Update UI
                    tvLatestOrderOrigin.text = "From: ${latestOrder.originCity}"
                    tvLatestOrderDestination.text = "To: ${latestOrder.destinationCity}"

                    // Set status with appropriate styling
                    tvLatestOrderStatus.text = "Status: ${latestOrder.status}"

                    when (latestOrder.status) {
                        "Accepted" -> {
                            tvLatestOrderStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.purple_500))
                        }
                        "In Progress" -> {
                            tvLatestOrderStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_700))
                        }
                        "Delivered", "Completed" -> {
                            tvLatestOrderStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                        }
                        else -> {
                            tvLatestOrderStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                        }
                    }

                    // Format the timestamp
                    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    val date = Date(latestOrder.timestamp)
                    tvLatestOrderTime.text = dateFormat.format(date)
                } else {
                    // Fallback if conversion to Order object fails
                    val originCity = document.getString("originCity") ?: "Unknown"
                    val destinationCity = document.getString("destinationCity") ?: "Unknown"
                    val status = document.getString("status") ?: "Unknown"
                    val timestamp = document.getLong("timestamp") ?: System.currentTimeMillis()

                    tvLatestOrderOrigin.text = "From: $originCity"
                    tvLatestOrderDestination.text = "To: $destinationCity"
                    tvLatestOrderStatus.text = "Status: $status"

                    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    val date = Date(timestamp)
                    tvLatestOrderTime.text = dateFormat.format(date)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading latest order: ${e.message}")
            withContext(Dispatchers.Main) {
                cardRecentOrders.visibility = View.GONE
            }
        }
    }

    private suspend fun loadEarningsSummary() {
        val currentUserId = auth.currentUser?.uid ?: return

        try {
            // Get current time boundaries
            val calendar = Calendar.getInstance()

            // Today: start of current day to now
            val todayStart = getStartOfDay(calendar.timeInMillis)

            // This week: 7 days ago to now
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            val weekStart = calendar.timeInMillis

            // This month: 1st day of current month to now
            calendar.timeInMillis = System.currentTimeMillis() // Reset to current time
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val monthStart = getStartOfDay(calendar.timeInMillis)

            // Current time for end of all periods
            val currentTime = System.currentTimeMillis()

            // Calculate earnings for each period
            val todayEarnings = calculateEarningsForPeriod(currentUserId, todayStart, currentTime)
            val weekEarnings = calculateEarningsForPeriod(currentUserId, weekStart, currentTime)
            val monthEarnings = calculateEarningsForPeriod(currentUserId, monthStart, currentTime)

            // Update UI
            withContext(Dispatchers.Main) {
                tvTodayEarnings.text = "${String.format("%.2f", todayEarnings)}"
                tvWeekEarnings.text = "${String.format("%.2f", weekEarnings)}"
                tvMonthEarnings.text = "${String.format("%.2f", monthEarnings)}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating earnings: ${e.message}")
            withContext(Dispatchers.Main) {
                tvTodayEarnings.text = "$0.00"
                tvWeekEarnings.text = "$0.00"
                tvMonthEarnings.text = "$0.00"
            }
        }
    }

    private suspend fun calculateEarningsForPeriod(userId: String, startTime: Long, endTime: Long): Double {
        var totalEarnings = 0.0

        try {
            // Query Firestore for completed orders
            val ordersSnapshot = withContext(Dispatchers.IO) {
                firestore.collection("orders")
                    .whereEqualTo("driverUid", userId)
                    .whereIn("status", listOf("Delivered", "Completed"))
                    .whereGreaterThanOrEqualTo("timestamp", startTime)
                    .whereLessThanOrEqualTo("timestamp", endTime)
                    .get()
                    .await()
            }

            // Sum up earnings for all matching orders
            for (doc in ordersSnapshot.documents) {
                val price = doc.getDouble("totalPrice") ?: 0.0
                totalEarnings += price
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating earnings for period: ${e.message}")
        }

        return totalEarnings
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this fragment
        CoroutineScope(Dispatchers.Main).launch {
            loadAllData()
        }
    }
}