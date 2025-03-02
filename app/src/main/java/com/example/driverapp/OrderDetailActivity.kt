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
import com.example.driverapp.services.ChatRepository
import com.example.driverapp.services.LocationService
import com.example.driverapp.services.OrderStatusTracker
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