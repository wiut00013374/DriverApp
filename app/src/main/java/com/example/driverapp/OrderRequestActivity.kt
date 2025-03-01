package com.example.driverapp

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.driverapp.data.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.concurrent.TimeUnit

class OrderRequestActivity : AppCompatActivity() {

    private val TAG = "OrderRequestActivity"

    // Views
    private lateinit var mapView: MapView
    private lateinit var tvOrigin: TextView
    private lateinit var tvDestination: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvPrice: TextView
    private lateinit var tvTruckType: TextView
    private lateinit var tvVolume: TextView
    private lateinit var tvWeight: TextView
    private lateinit var tvTimeRemaining: TextView
    private lateinit var progressBarTimer: ProgressBar
    private lateinit var btnAccept: Button
    private lateinit var btnReject: Button

    // Firebase
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Data
    private var orderId: String? = null
    private var order: Order? = null

    // Timer
    private var countDownTimer: CountDownTimer? = null
    private val RESPONSE_TIMEOUT_MS = 60000L // 60 seconds to respond

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_request)

        // Initialize OSMDroid
        Configuration.getInstance().userAgentValue = packageName

        // Initialize views
        initViews()

        // Get order ID from intent
        orderId = intent.getStringExtra("order_id")
        if (orderId == null) {
            Toast.makeText(this, "Invalid order request", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Fetch order details
        fetchOrderDetails(orderId!!)

        // Setup buttons
        setupButtons()

        // Start countdown timer
        startResponseTimer()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapOrderRequest)
        tvOrigin = findViewById(R.id.tvRequestOrigin)
        tvDestination = findViewById(R.id.tvRequestDestination)
        tvDistance = findViewById(R.id.tvRequestDistance)
        tvPrice = findViewById(R.id.tvRequestPrice)
        tvTruckType = findViewById(R.id.tvRequestTruckType)
        tvVolume = findViewById(R.id.tvRequestVolume)
        tvWeight = findViewById(R.id.tvRequestWeight)
        tvTimeRemaining = findViewById(R.id.tvTimeRemaining)
        progressBarTimer = findViewById(R.id.progressBarTimer)
        btnAccept = findViewById(R.id.btnAcceptRequest)
        btnReject = findViewById(R.id.btnRejectRequest)

        // Initialize map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(12.0)
    }

    private fun fetchOrderDetails(orderId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val orderDoc = firestore.collection("orders").document(orderId).get().await()
                val fetchedOrder = orderDoc.toObject(Order::class.java)?.apply {
                    id = orderDoc.id
                }

                if (fetchedOrder != null) {
                    order = fetchedOrder

                    // Update UI on main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        displayOrderDetails(fetchedOrder)
                        setupMap(fetchedOrder)
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(this@OrderRequestActivity, "Order not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching order: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@OrderRequestActivity, "Error loading order: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun displayOrderDetails(order: Order) {
        tvOrigin.text = "From: ${order.originCity}"
        tvDestination.text = "To: ${order.destinationCity}"
        tvPrice.text = "Price: $${String.format("%.2f", order.totalPrice)}"

        // Calculate and display the distance
        val distanceKm = calculateDistanceKm(
            order.originLat, order.originLon,
            order.destinationLat, order.destinationLon
        )
        tvDistance.text = "Distance: ${String.format("%.1f", distanceKm)} km"

        tvTruckType.text = "Truck Type: ${order.truckType}"
        tvVolume.text = "Volume: ${order.volume} m³"
        tvWeight.text = "Weight: ${order.weight} kg"
    }

    private fun setupMap(order: Order) {
        // Add origin marker
        val originPoint = GeoPoint(order.originLat, order.originLon)
        val originMarker = Marker(mapView)
        originMarker.position = originPoint
        originMarker.title = "Origin: ${order.originCity}"
        originMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(originMarker)

        // Add destination marker
        val destPoint = GeoPoint(order.destinationLat, order.destinationLon)
        val destMarker = Marker(mapView)
        destMarker.position = destPoint
        destMarker.title = "Destination: ${order.destinationCity}"
        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(destMarker)

        // Draw a simple line between origin and destination
        val routeLine = Polyline()
        routeLine.addPoint(originPoint)
        routeLine.addPoint(destPoint)
        routeLine.color = ContextCompat.getColor(this, R.color.purple_500)
        routeLine.width = 5f
        mapView.overlays.add(routeLine)

        // Zoom to include both points
        mapView.zoomToBoundingBox(routeLine.bounds.increaseByScale(1.5f), true)
        mapView.invalidate()
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon/2) * Math.sin(dLon/2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
        return R * c
    }

    private fun setupButtons() {
        btnAccept.setOnClickListener {
            acceptOrder()
        }

        btnReject.setOnClickListener {
            rejectOrder()
        }
    }

    private fun acceptOrder() {
        val currentOrderId = orderId ?: return
        val currentUserId = auth.currentUser?.uid ?: return

        // Disable buttons to prevent multiple clicks
        btnAccept.isEnabled = false
        btnReject.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // First check if the order is still available
                val orderDoc = firestore.collection("orders").document(currentOrderId).get().await()

                // If order already has a driver, it's too late
                if (orderDoc.getString("driverUid") != null) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            this@OrderRequestActivity,
                            "This order has already been accepted by another driver",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                    return@launch
                }

                // Get the drivers contact list
                @Suppress("UNCHECKED_CAST")
                val driversContactList = orderDoc.get("driversContactList") as? Map<String, String>

                if (driversContactList == null || !driversContactList.containsKey(currentUserId)) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            this@OrderRequestActivity,
                            "You are not authorized to accept this order",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                    return@launch
                }

                // Update the order with this driver and change status
                val updates = hashMapOf<String, Any>(
                    "driverUid" to currentUserId,
                    "status" to "Accepted",
                    "acceptedAt" to System.currentTimeMillis()
                )

                // Also update the driver's status in the contact list
                val updatedList = driversContactList.toMutableMap()
                updatedList[currentUserId] = "accepted"
                updates["driversContactList"] = updatedList

                // Update the order
                firestore.collection("orders").document(currentOrderId)
                    .update(updates)
                    .await()

                // Cancel the timer
                cancelTimer()

                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@OrderRequestActivity,
                        "Order accepted successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate to OrderDetailActivity to see full order details
                    val intent = Intent(this@OrderRequestActivity, OrderDetailActivity::class.java)
                    intent.putExtra("EXTRA_ORDER_ID", currentOrderId)
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error accepting order: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@OrderRequestActivity,
                        "Error accepting order: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Re-enable buttons
                    btnAccept.isEnabled = true
                    btnReject.isEnabled = true
                }
            }
        }
    }

    private fun rejectOrder() {
        val currentOrderId = orderId ?: return
        val currentUserId = auth.currentUser?.uid ?: return

        // Disable buttons to prevent multiple clicks
        btnAccept.isEnabled = false
        btnReject.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get the order data
                val orderDoc = firestore.collection("orders").document(currentOrderId).get().await()

                // Get the drivers contact list
                @Suppress("UNCHECKED_CAST")
                val driversContactList = orderDoc.get("driversContactList") as? Map<String, String>

                if (driversContactList == null || !driversContactList.containsKey(currentUserId)) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            this@OrderRequestActivity,
                            "You are not authorized to reject this order",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                    return@launch
                }

                // Update the driver's status in the contact list
                val updatedList = driversContactList.toMutableMap()
                updatedList[currentUserId] = "rejected"

                // Get the current index
                val currentIndex = orderDoc.getLong("currentDriverIndex")?.toInt() ?: 0

                // Update the order with the rejected status and increment the index
                firestore.collection("orders").document(currentOrderId)
                    .update(
                        mapOf(
                            "driversContactList" to updatedList,
                            "currentDriverIndex" to currentIndex + 1
                        )
                    )
                    .await()

                // Cancel the timer
                cancelTimer()

                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@OrderRequestActivity,
                        "Order rejected",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting order: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@OrderRequestActivity,
                        "Error rejecting order: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Re-enable buttons
                    btnAccept.isEnabled = true
                    btnReject.isEnabled = true
                }
            }
        }
    }

    private fun startResponseTimer() {
        progressBarTimer.max = RESPONSE_TIMEOUT_MS.toInt()
        progressBarTimer.progress = RESPONSE_TIMEOUT_MS.toInt()

        countDownTimer = object : CountDownTimer(RESPONSE_TIMEOUT_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) -
                        TimeUnit.MINUTES.toSeconds(minutes)

                tvTimeRemaining.text = String.format("%02d:%02d", minutes, seconds)
                progressBarTimer.progress = millisUntilFinished.toInt()

                // Make timer red when less than 10 seconds remain
                if (millisUntilFinished <= 10000) {
                    tvTimeRemaining.setTextColor(ContextCompat.getColor(this@OrderRequestActivity, android.R.color.holo_red_light))
                }
            }

            override fun onFinish() {
                tvTimeRemaining.text = "00:00"
                progressBarTimer.progress = 0

                // Auto-reject the order when time expires
                if (btnReject.isEnabled) { // Only if not already accepted/rejected
                    rejectOrder()
                }
            }
        }.start()
    }

    private fun cancelTimer() {
        countDownTimer?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimer()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}