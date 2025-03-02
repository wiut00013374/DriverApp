package com.example.driverapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.driverapp.data.Order
import com.example.driverapp.services.LocationService
import com.example.driverapp.services.OrderStatusTracker
import com.example.myapp.repos.ChatRepository
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

class OrderDetailActivity : AppCompatActivity() {

    private val TAG = "OrderDetailActivity"
    private lateinit var mapView: MapView
    private lateinit var tvOriginCity: TextView
    private lateinit var tvDestinationCity: TextView
    private lateinit var tvPrice: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvTruckType: TextView
    private lateinit var tvVolume: TextView
    private lateinit var tvWeight: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLoadingMessage: TextView

    // Buttons for order actions
    private lateinit var btnAccept: Button
    private lateinit var btnStartTrip: Button
    private lateinit var btnPickedUp: Button
    private lateinit var btnDelivered: Button
    private lateinit var btnCancelDelivery: Button
    private lateinit var btnContactCustomer: Button

    private var order: Order? = null
    private var orderId: String? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Location tracking listener
    private var orderListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_detail)

        // Set up map view
        Configuration.getInstance().userAgentValue = packageName
        mapView = findViewById(R.id.mapOrderRoute)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Initialize views
        initializeViews()

        // Get order from intent
        order = intent.getParcelableExtra("EXTRA_ORDER")
        orderId = intent.getStringExtra("EXTRA_ORDER_ID")

        if (order != null) {
            // If we have the order object, display it immediately
            displayOrderDetails(order!!)
            loadRouteAndUpdateMap(order!!)
        } else if (orderId != null) {
            // If we only have the order ID, fetch the order from Firestore
            fetchOrderById(orderId!!)
        } else {
            Toast.makeText(this, "Order data not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set up button listeners
        setupButtonListeners()

        // Set up order listener for real-time updates
        setupOrderListener()
    }

    private fun initializeViews() {
        tvOriginCity = findViewById(R.id.tvDetailOriginCity)
        tvDestinationCity = findViewById(R.id.tvDetailDestinationCity)
        tvPrice = findViewById(R.id.tvDetailPrice)
        tvDistance = findViewById(R.id.tvDetailDistance)
        tvTruckType = findViewById(R.id.tvDetailTruckType)
        tvVolume = findViewById(R.id.tvDetailVolume)
        tvWeight = findViewById(R.id.tvDetailWeight)
        tvStatus = findViewById(R.id.tvDetailStatus)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)

        btnAccept = findViewById(R.id.btnDetailAccept)
        btnStartTrip = findViewById(R.id.btnDetailStartTrip)
        btnPickedUp = findViewById(R.id.btnDetailPickedUp)
        btnDelivered = findViewById(R.id.btnDetailDelivered)
        btnCancelDelivery = findViewById(R.id.btnDetailCancelDelivery)
        btnContactCustomer = findViewById(R.id.btnDetailContactCustomer)
    }

    private fun fetchOrderById(orderId: String) {
        tvLoadingMessage.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val orderDoc = firestore.collection("orders").document(orderId).get().await()
                val fetchedOrder = orderDoc.toObject(Order::class.java)?.apply {
                    id = orderDoc.id
                }

                CoroutineScope(Dispatchers.Main).launch {
                    tvLoadingMessage.visibility = View.GONE

                    if (fetchedOrder != null) {
                        order = fetchedOrder
                        displayOrderDetails(fetchedOrder)
                        loadRouteAndUpdateMap(fetchedOrder)
                    } else {
                        Toast.makeText(this@OrderDetailActivity, "Order not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching order: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    tvLoadingMessage.visibility = View.GONE
                    Toast.makeText(this@OrderDetailActivity, "Error loading order: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun displayOrderDetails(order: Order) {
        tvOriginCity.text = "Origin: ${order.originCity}"
        tvDestinationCity.text = "Destination: ${order.destinationCity}"
        tvPrice.text = "Price: $${String.format("%.2f", order.totalPrice)}"

        // Calculate and display the distance
        val distanceKm = calculateDistance(
            order.originLat, order.originLon,
            order.destinationLat, order.destinationLon
        )
        tvDistance.text = "Distance: ${String.format("%.1f", distanceKm)} km"

        tvTruckType.text = "Truck Type: ${order.truckType}"
        tvVolume.text = "Volume: ${order.volume} m³"
        tvWeight.text = "Weight: ${order.weight} kg"
        tvStatus.text = "Status: ${order.status}"

        // Configure buttons based on order status
        updateButtonVisibility(order)
    }

    private fun loadRouteAndUpdateMap(order: Order) {
        // Add origin marker
        val originPoint = GeoPoint(order.originLat, order.originLon)
        val originMarker = Marker(mapView)
        originMarker.position = originPoint
        originMarker.title = "Origin: ${order.originCity}"
        originMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        originMarker.icon = ContextCompat.getDrawable(this, R.drawable.ic_pickup)
        mapView.overlays.add(originMarker)

        // Add destination marker
        val destPoint = GeoPoint(order.destinationLat, order.destinationLon)
        val destMarker = Marker(mapView)
        destMarker.position = destPoint
        destMarker.title = "Destination: ${order.destinationCity}"
        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        destMarker.icon = ContextCompat.getDrawable(this, R.drawable.ic_delivery)
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

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon/2) * Math.sin(dLon/2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
        return R * c
    }

    private fun updateButtonVisibility(order: Order) {
        // Hide all buttons initially
        btnAccept.visibility = View.GONE
        btnStartTrip.visibility = View.GONE
        btnPickedUp.visibility = View.GONE
        btnDelivered.visibility = View.GONE
        btnCancelDelivery.visibility = View.GONE
        btnContactCustomer.visibility = View.GONE

        // Get the current user ID
        val currentUserId = auth.currentUser?.uid

        // Check if this order is assigned to the current driver
        val isAssignedToMe = order.driverUid == currentUserId

        // Check if this driver is in the contact list
        val canAcceptOrReject = order.driversContacted?.containsKey(currentUserId ?: "") == true &&
                order.driversContacted[currentUserId]?.equals("notified", ignoreCase = true) == true

        // Show buttons based on order status and assignment
        when (order.status) {
            "Pending" -> {
                if (canAcceptOrReject) {
                    btnAccept.visibility = View.VISIBLE
                }
            }
            "Accepted" -> {
                if (isAssignedToMe) {
                    btnStartTrip.visibility = View.VISIBLE
                    btnCancelDelivery.visibility = View.VISIBLE
                    btnContactCustomer.visibility = View.VISIBLE
                }
            }
            "In Progress" -> {
                if (isAssignedToMe) {
                    btnPickedUp.visibility = View.VISIBLE
                    btnContactCustomer.visibility = View.VISIBLE
                }
            }
            "Picked Up" -> {
                if (isAssignedToMe) {
                    btnDelivered.visibility = View.VISIBLE
                    btnContactCustomer.visibility = View.VISIBLE
                }
            }
            "Delivered", "Completed" -> {
                if (isAssignedToMe) {
                    btnContactCustomer.visibility = View.VISIBLE
                }
            }
        }
    }
    private fun setupButtonListeners() {
        // Accept order button
        btnAccept.setOnClickListener {
            acceptOrder()
        }

        // Start trip button
        btnStartTrip.setOnClickListener {
            startDeliveryTrip()
        }

        // Picked up button
        btnPickedUp.setOnClickListener {
            markOrderAsPickedUp()
        }

        // Delivered button
        btnDelivered.setOnClickListener {
            markOrderAsDelivered()
        }

        // Cancel delivery button
        btnCancelDelivery.setOnClickListener {
            cancelDelivery()
        }

        // Contact customer button
        btnContactCustomer.setOnClickListener {
            contactCustomer()
        }
    }

    private fun acceptOrder() {
        val currentOrder = order ?: return
        val currentUserId = auth.currentUser?.uid ?: return

        // Disable buttons to prevent multiple clicks
        disableActionButtons()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if the order is still available
                val orderDoc = firestore.collection("orders").document(currentOrder.id).get().await()

                // If order already has a driver, it's too late
                if (orderDoc.getString("driverUid") != null) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            this@OrderDetailActivity,
                            "This order has already been accepted by another driver",
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

                // Also update the driver's status in the contact list if it exists
                val driversContactList = orderDoc.get("driversContactList") as? Map<String, String>
                if (driversContactList != null) {
                    val updatedList = driversContactList.toMutableMap()
                    updatedList[currentUserId] = "accepted"
                    updates["driversContactList"] = updatedList
                }

                // Update the order
                firestore.collection("orders").document(currentOrder.id)
                    .update(updates)
                    .await()

                // Start location service for this order
                CoroutineScope(Dispatchers.Main).launch {
                    LocationService.startForOrder(this@OrderDetailActivity, currentOrder.id)
                    Toast.makeText(
                        this@OrderDetailActivity,
                        "Order accepted! Starting location tracking.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Refresh order details to update button visibility
                    fetchOrderById(currentOrder.id)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error accepting order: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@OrderDetailActivity,
                        "Error accepting order: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    enableActionButtons()
                }
            }
        }
    }

    private fun startDeliveryTrip() {
        val currentOrder = order ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update order status to "In Progress"
                firestore.collection("orders").document(currentOrder.id)
                    .update("status", "In Progress")
                    .await()

                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@OrderDetailActivity,
                        "Trip started! Navigate to pickup location.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Refresh order details
                    fetchOrderById(currentOrder.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting trip: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@OrderDetailActivity,
                        "Error starting trip: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun markOrderAsPickedUp() {
        val currentOrder = order ?: return

        // Check if driver is close enough to pickup location
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get the latest order data to check if driver can pickup
                val orderDoc = firestore.collection("orders").document(currentOrder.id)
                    .get()
                    .await()

                val driverCanPickup = orderDoc.getBoolean("driverCanPickup") ?: false

                if (!driverCanPickup) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            this@OrderDetailActivity,
                            "You must be within 10km of the pickup location",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                // Update order status to "Picked Up"
                firestore.collection("orders").document(currentOrder.id)
                    .update(
                        mapOf(
                            "status" to "Picked Up",
                            "pickedUpAt" to System.currentTimeMillis()
                        )
                    )
                    .await()

                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@OrderDetailActivity,
                        "Order marked as picked up! Navigate to delivery location.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Refresh order details
                    fetchOrderById(currentOrder.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking order as picked up: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@OrderDetailActivity,
                        "Error marking order as picked up: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun markOrderAsDelivered() {
        val currentOrder = order ?: return

        // Check if driver is close enough to delivery location
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get the latest order data to check if driver can deliver
                val orderDoc = firestore.collection("orders").document(currentOrder.id)
                    .get()
                    .await()

                val driverCanDeliver = orderDoc.getBoolean("driverCanDeliver") ?: false

                if (!driverCanDeliver) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(
                            this@OrderDetailActivity,
                            "You must be within 10km of the delivery location",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                // Update order status to "Delivered"
                firestore.collection("orders").document(currentOrder.id)
                    .update(
                        mapOf(
                            "status" to "Delivered",
                            "deliveredAt" to System.currentTimeMillis()
                        )
                    )
                    .await()

                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@OrderDetailActivity,
                        "Order successfully delivered!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Stop location service
                    LocationService.stop(this@OrderDetailActivity)

                    // Refresh order details
                    fetchOrderById(currentOrder.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error marking order as delivered: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        this@OrderDetailActivity,
                        "Error marking order as delivered: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun cancelDelivery() {
        val currentOrder = order ?: return

        AlertDialog.Builder(this)
            .setTitle("Cancel Delivery")
            .setMessage("Are you sure you want to cancel this delivery? This action cannot be undone.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Update order status to "Cancelled"
                        firestore.collection("orders").document(currentOrder.id)
                            .update(
                                mapOf(
                                    "status" to "Cancelled",
                                    "cancelledAt" to System.currentTimeMillis(),
                                    "cancelledBy" to "driver"
                                )
                            )
                            .await()

                        // Stop location service
                        LocationService.stop(this@OrderDetailActivity)

                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(
                                this@OrderDetailActivity,
                                "Delivery cancelled",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cancelling delivery: ${e.message}")
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(
                                this@OrderDetailActivity,
                                "Error cancelling delivery: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun contactCustomer() {
        val currentOrder = order ?: return
        val currentUserId = auth.currentUser?.uid ?: return
        val customerUid = currentOrder.uid

        if (customerUid.isBlank()) {
            Toast.makeText(this, "Customer information not available", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading indicator
        val loadingDialog = AlertDialog.Builder(this)
            .setView(layoutInflater.inflate(R.layout.dialog_loading, null))
            .setCancelable(false)
            .create()
        loadingDialog.show()

        // Create or get existing chat
        ChatRepository.createOrGetChat(currentOrder.id, currentUserId, customerUid) { chatId ->
            loadingDialog.dismiss()

            if (chatId != null) {
                // Open chat activity
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("EXTRA_CHAT_ID", chatId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Failed to create chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun disableActionButtons() {
        btnAccept.isEnabled = false
        btnStartTrip.isEnabled = false
        btnPickedUp.isEnabled = false
        btnDelivered.isEnabled = false
        btnCancelDelivery.isEnabled = false
    }

    private fun enableActionButtons() {
        btnAccept.isEnabled = true
        btnStartTrip.isEnabled = true
        btnPickedUp.isEnabled = true
        btnDelivered.isEnabled = true
        btnCancelDelivery.isEnabled = true
    }

    private fun setupOrderListener() {
        // Get the order ID
        val orderIdValue = orderId ?: order?.id ?: return

        // Set up real-time listener for order updates
        orderListener = firestore.collection("orders").document(orderIdValue)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for order updates: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    // Convert to Order object
                    val updatedOrder = snapshot.toObject(Order::class.java)?.apply {
                        id = snapshot.id
                    }

                    if (updatedOrder != null) {
                        // Update local order object
                        order = updatedOrder

                        // Update UI
                        displayOrderDetails(updatedOrder)

                        // Check if UI needs updating based on proximity flags
                        val driverCanPickup = snapshot.getBoolean("driverCanPickup") ?: false
                        val driverCanDeliver = snapshot.getBoolean("driverCanDeliver") ?: false

                        // Enable/disable buttons based on proximity
                        if (updatedOrder.status == "In Progress") {
                            btnPickedUp.isEnabled = driverCanPickup
                            if (driverCanPickup) {
                                btnPickedUp.text = "Confirm Pickup (Within Range)"
                            } else {
                                btnPickedUp.text = "Pickup (Get Closer)"
                            }
                        } else if (updatedOrder.status == "Picked Up") {
                            btnDelivered.isEnabled = driverCanDeliver
                            if (driverCanDeliver) {
                                btnDelivered.text = "Confirm Delivery (Within Range)"
                            } else {
                                btnDelivered.text = "Deliver (Get Closer)"
                            }
                        }
                    }
                }
            }
    }
}