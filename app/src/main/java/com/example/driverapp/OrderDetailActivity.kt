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
import com.example.driverapp.services.NotificationService
import com.example.myapp.repos.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private lateinit var tvProximityInfo: TextView
    private lateinit var tvEtaInfo: TextView

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

    // Driver location marker
    private var driverMarker: Marker? = null

    // Route polyline
    private var routePolyline: Polyline? = null

    // Order listener
    private var orderListener: com.google.firebase.firestore.ListenerRegistration? = null

    // Loading dialog
    private var loadingDialog: AlertDialog? = null

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

        // Initialize loading dialog
        initializeLoadingDialog()

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
        tvProximityInfo = findViewById(R.id.tvProximityInfo)
        tvEtaInfo = findViewById(R.id.tvEtaInfo)

        btnAccept = findViewById(R.id.btnDetailAccept)
        btnStartTrip = findViewById(R.id.btnDetailStartTrip)
        btnPickedUp = findViewById(R.id.btnDetailPickedUp)
        btnDelivered = findViewById(R.id.btnDetailDelivered)
        btnCancelDelivery = findViewById(R.id.btnDetailCancelDelivery)
        btnContactCustomer = findViewById(R.id.btnDetailContactCustomer)
    }

    private fun initializeLoadingDialog() {
        loadingDialog = AlertDialog.Builder(this)
            .setView(layoutInflater.inflate(R.layout.dialog_loading, null))
            .setCancelable(false)
            .create()
    }

    private fun showLoadingDialog() {
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }

    private fun fetchOrderById(orderId: String) {
        showLoadingDialog()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val orderDoc = firestore.collection("orders").document(orderId).get().await()
                val fetchedOrder = orderDoc.toObject(Order::class.java)?.apply {
                    id = orderDoc.id
                }

                withContext(Dispatchers.Main) {
                    hideLoadingDialog()

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
                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
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

        // Display proximity information
        updateProximityInfo(order)

        // Update ETA information
        updateEtaInfo(order)

        // Configure buttons based on order status
        updateButtonVisibility(order)
    }

    private fun updateProximityInfo(order: Order) {
        val currentUserId = auth.currentUser?.uid
        val isDriverOfThisOrder = order.driverUid == currentUserId

        if (isDriverOfThisOrder) {
            tvProximityInfo.visibility = View.VISIBLE

            when (order.status) {
                "Accepted", "In Progress" -> {
                    val distance = order.distanceToPickup
                    val canPickup = order.driverCanPickup

                    if (canPickup) {
                        tvProximityInfo.text = "You are within 10km of the pickup location"
                        tvProximityInfo.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    } else {
                        tvProximityInfo.text = "Distance to pickup: ${String.format("%.1f", distance)} km"
                        tvProximityInfo.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                    }
                }
                "Picked Up" -> {
                    val distance = order.distanceToDelivery
                    val canDeliver = order.driverCanDeliver

                    if (canDeliver) {
                        tvProximityInfo.text = "You are within 10km of the delivery location"
                        tvProximityInfo.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    } else {
                        tvProximityInfo.text = "Distance to delivery: ${String.format("%.1f", distance)} km"
                        tvProximityInfo.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                    }
                }
                else -> {
                    tvProximityInfo.visibility = View.GONE
                }
            }
        } else {
            tvProximityInfo.visibility = View.GONE
        }
    }

    private fun updateEtaInfo(order: Order) {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        tvEtaInfo.visibility = View.VISIBLE

        when (order.status) {
            "Accepted", "In Progress" -> {
                val pickupEta = order.pickupEta
                if (pickupEta > 0) {
                    val etaDate = Date(pickupEta)
                    tvEtaInfo.text = "Estimated pickup time: ${dateFormat.format(etaDate)}"
                } else {
                    tvEtaInfo.text = "Calculating ETA to pickup..."
                }
            }
            "Picked Up" -> {
                val deliveryEta = order.deliveryEta
                if (deliveryEta > 0) {
                    val etaDate = Date(deliveryEta)
                    tvEtaInfo.text = "Estimated delivery time: ${dateFormat.format(etaDate)}"
                } else {
                    tvEtaInfo.text = "Calculating ETA to delivery..."
                }
            }
            "Delivered", "Completed" -> {
                tvEtaInfo.text = "Order completed"
            }
            else -> {
                tvEtaInfo.visibility = View.GONE
            }
        }
    }

    private fun loadRouteAndUpdateMap(order: Order) {
        // Clear existing overlays
        mapView.overlays.clear()

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
        routePolyline = Polyline()
        routePolyline?.addPoint(originPoint)
        routePolyline?.addPoint(destPoint)
        routePolyline?.color = ContextCompat.getColor(this, R.color.purple_500)
        routePolyline?.width = 5f
        mapView.overlays.add(routePolyline)

        // Add driver location marker if available
        if (order.driverLocation != null) {
            val driverPoint = GeoPoint(
                order.driverLocation!!.latitude,
                order.driverLocation!!.longitude
            )
            driverMarker = Marker(mapView)
            driverMarker?.position = driverPoint
            driverMarker?.title = "Driver Location"
            driverMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            driverMarker?.icon = ContextCompat.getDrawable(this, R.drawable.ic_driver_location)

            // Rotate marker based on heading
            if (order.driverHeading != 0f) {
                driverMarker?.rotation = order.driverHeading
            }

            mapView.overlays.add(driverMarker)
        }

        // Zoom to include all points
        val boundsBuilder = org.osmdroid.util.BoundingBox.builder()
        boundsBuilder.include(originPoint)
        boundsBuilder.include(destPoint)

        // Include driver location in bounds if available
        if (order.driverLocation != null) {
            boundsBuilder.include(
                GeoPoint(
                    order.driverLocation!!.latitude,
                    order.driverLocation!!.longitude
                )
            )
        }

        mapView.zoomToBoundingBox(boundsBuilder.build().increaseByScale(1.2f), true)
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

        // Show buttons based on order status and assignment
        when (order.status) {
            "Pending" -> {
                if (order.driversContacted?.containsKey(currentUserId ?: "") == true) {
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

                    // Update button state based on proximity to pickup
                    if (order.driverCanPickup) {
                        btnPickedUp.isEnabled = true
                        btnPickedUp.text = "Confirm Pickup (Within Range)"
                        btnPickedUp.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    } else {
                        btnPickedUp.isEnabled = false
                        btnPickedUp.text = "Pickup (Get Closer: 10km Required)"
                        btnPickedUp.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                    }
                }
            }
            "Picked Up" -> {
                if (isAssignedToMe) {
                    btnDelivered.visibility = View.VISIBLE
                    btnContactCustomer.visibility = View.VISIBLE

                    // Update button state based on proximity to delivery
                    if (order.driverCanDeliver) {
                        btnDelivered.isEnabled = true
                        btnDelivered.text = "Confirm Delivery (Within Range)"
                        btnDelivered.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    } else {
                        btnDelivered.isEnabled = false
                        btnDelivered.text = "Deliver (Get Closer: 10km Required)"
                        btnDelivered.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
                    }
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
                    withContext(Dispatchers.Main) {
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
                withContext(Dispatchers.Main) {
                    LocationService.startForOrder(this@OrderDetailActivity, currentOrder.id)
                    Toast.makeText(
                        this@OrderDetailActivity,
                        "Order accepted! Location tracking started.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Refresh order details to update button visibility
                    fetchOrderById(currentOrder.id)
                }

                // Notify customer about driver accepting the order
                val customerUid = orderDoc.getString("uid")
                if (customerUid != null) {
                    NotificationService.sendCustomerOrderUpdate(
                        this@OrderDetailActivity,
                        customerUid,
                        currentOrder.id,
                        "Order Accepted",
                        "A driver has accepted your order and is on the way to pickup location"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error accepting order: ${e.message}")
                withContext(Dispatchers.Main) {
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

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@OrderDetailActivity,
                        "Trip started! Navigate to pickup location.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Refresh order details
                    fetchOrderById(currentOrder.id)
                }

                // Notify customer
                val customerUid = currentOrder.uid
                if (customerUid.isNotEmpty()) {
                    NotificationService.sendCustomerOrderUpdate(
                        this@OrderDetailActivity,
                        customerUid,
                        currentOrder.id,
                        "Driver En Route",
                        "The driver is now heading to the pickup location"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting trip: ${e.message}")
                withContext(Dispatchers.Main) {
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
        if (!currentOrder.driverCanPickup) {
            Toast.makeText(
                this,
                "You must be within 10km of the pickup location",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Call the LocationService method to mark the order as picked up
        LocationService.markOrderPickedUp(this, currentOrder.id)

        Toast.makeText(
            this,
            "Order marked as picked up! Now head to delivery location.",
            Toast.LENGTH_SHORT
        ).show()

        // Refresh the order details
        fetchOrderById(currentOrder.id)
    }

    private fun markOrderAsDelivered() {
        val currentOrder = order ?: return

        // Check if driver is close enough to delivery location
        if (!currentOrder.driverCanDeliver) {
            Toast.makeText(
                this,
                "You must be within 10km of the delivery location",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Call the LocationService method to mark the order as delivered
        LocationService.markOrderDelivered(this, currentOrder.id)

        Toast.makeText(
            this,
            "Order successfully delivered!",
            Toast.LENGTH_SHORT
        ).show()

        // Refresh the order details
        fetchOrderById(currentOrder.id)
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

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@OrderDetailActivity,
                                "Delivery cancelled",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }

                        // Notify customer
                        val customerUid = currentOrder.uid
                        if (customerUid.isNotEmpty()) {
                            NotificationService.sendCustomerOrderUpdate(
                                this@OrderDetailActivity,
                                customerUid,
                                currentOrder.id,
                                "Order Cancelled",
                                "The driver has cancelled your order"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cancelling delivery: ${e.message}")
                        withContext(Dispatchers.Main) {
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
        showLoadingDialog()

        // Create or get existing chat
        ChatRepository.createOrGetChat(currentOrder.id, currentUserId, customerUid) { chatId ->
            hideLoadingDialog()

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
        // Don't enable these as they depend on proximity
        // btnPickedUp.isEnabled = true
        // btnDelivered.isEnabled = true
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

                        // Update the map with latest driver location
                        updateDriverLocation(updatedOrder)
                    }
                }
            }
    }

    private fun updateDriverLocation(order: Order) {
        if (order.driverLocation != null) {
            val driverPoint = GeoPoint(
                order.driverLocation!!.latitude,
                order.driverLocation!!.longitude
            )

            // Update existing marker or create a new one
            if (driverMarker == null) {
                driverMarker = Marker(mapView)
                driverMarker?.title = "Driver Location"
                driverMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                driverMarker?.icon = ContextCompat.getDrawable(this, R.drawable.ic_driver_location)
                mapView.overlays.add(driverMarker)
            }

            // Update marker position
            driverMarker?.position = driverPoint

            // Update marker rotation based on heading
            if (order.driverHeading != 0f) {
                driverMarker?.rotation = order.driverHeading
            }

            // Update map bounds to include all points
            val boundsBuilder = org.osmdroid.util.BoundingBox.builder()

            // Include origin and destination points
            boundsBuilder.include(
                GeoPoint(
                    order.originLat,
                    order.originLon
                )
            )
            boundsBuilder.include(
                GeoPoint(
                    order.destinationLat,
                    order.destinationLon
                )
            )

            // Include driver location
            boundsBuilder.include(driverPoint)

            // Apply the bounds
            mapView.zoomToBoundingBox(boundsBuilder.build().increaseByScale(1.2f), true)

            // Redraw the map
            mapView.invalidate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove listener to prevent memory leaks
        orderListener?.remove()
        // Clean up loading dialog
        loadingDialog?.dismiss()
    }

    override fun onResume() {
        super.onResume()
        // Resume map
        mapView.onResume()

        // Refresh order data
        orderId?.let { fetchOrderById(it) }
    }

    override fun onPause() {
        super.onPause()
        // Pause map
        mapView.onPause()
    }
}