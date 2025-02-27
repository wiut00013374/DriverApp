package com.example.driverapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.driverapp.data.Order
import com.example.driverapp.repos.DriverOrderRepository
import com.example.myapp.repos.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URL
import javax.net.ssl.HttpsURLConnection

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
    private lateinit var btnAccept: Button
    private lateinit var btnReject: Button
    private lateinit var btnStartDelivery: Button
    private lateinit var btnCompleteDelivery: Button
    private lateinit var btnCancelDelivery: Button
    private lateinit var btnContactCustomer: Button

    private var order: Order? = null
    private var orderId: String? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_detail)

        // Set up map view
        Configuration.getInstance().userAgentValue = packageName
        mapView = findViewById(R.id.mapOrderRoute)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Initialize views
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
        btnReject = findViewById(R.id.btnDetailReject)
        btnStartDelivery = findViewById(R.id.btnDetailStartDelivery)
        btnCompleteDelivery = findViewById(R.id.btnDetailCompleteDelivery)
        btnCancelDelivery = findViewById(R.id.btnDetailCancelDelivery)
        btnContactCustomer = findViewById(R.id.btnDetailContactCustomer)

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
        mapView.overlays.add(originMarker)

        // Add destination marker
        val destPoint = GeoPoint(order.destinationLat, order.destinationLon)
        val destMarker = Marker(mapView)
        destMarker.position = destPoint
        destMarker.title = "Destination: ${order.destinationCity}"
        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(destMarker)

        // Fetch the route from OSRM
        CoroutineScope(Dispatchers.IO).launch {
            val routePoints = getRoute(
                order.originLat, order.originLon,
                order.destinationLat, order.destinationLon
            )

            CoroutineScope(Dispatchers.Main).launch {
                if (routePoints != null && routePoints.isNotEmpty()) {
                    // Draw the route
                    val polyline = Polyline(mapView)
                    polyline.setPoints(routePoints)
                    polyline.color = Color.BLUE
                    polyline.width = 5f
                    mapView.overlays.add(polyline)

                    // Zoom to show the entire route
                    mapView.zoomToBoundingBox(polyline.bounds.increaseByScale(1.5f), true)

                    // Calculate the actual route distance
                    val routeDistanceKm = calculateRouteDistance(routePoints) / 1000.0
                    tvDistance.text = "Distance: ${String.format("%.1f", routeDistanceKm)} km"
                } else {
                    // If route fetching fails, just show a simple line and zoom to include both points
                    val simplePolyline = Polyline(mapView)
                    simplePolyline.addPoint(originPoint)
                    simplePolyline.addPoint(destPoint)
                    simplePolyline.color = Color.RED
                    simplePolyline.width = 5f
                    mapView.overlays.add(simplePolyline)
                    mapView.zoomToBoundingBox(simplePolyline.bounds.increaseByScale(1.5f), true)
                }

                mapView.invalidate()
            }
        }
    }

    private suspend fun getRoute(
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double
    ): List<GeoPoint>? {
        try {
            val urlStr = "https://router.project-osrm.org/route/v1/driving/$originLon,$originLat;$destLon,$destLat?overview=full&geometries=geojson"
            val connection = URL(urlStr).openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                if (json.has("code") && json.getString("code") == "Ok") {
                    val route = json.getJSONArray("routes").getJSONObject(0)
                    val geometry = route.getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")

                    val points = mutableListOf<GeoPoint>()
                    for (i in 0 until coordinates.length()) {
                        val point = coordinates.getJSONArray(i)
                        points.add(GeoPoint(point.getDouble(1), point.getDouble(0))) // lat, lon
                    }

                    return points
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching route: ${e.message}")
        }

        return null
    }

    private fun calculateRouteDistance(points: List<GeoPoint>): Double {
        var distance = 0.0
        for (i in 0 until points.size - 1) {
            distance += calculateDistance(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            ) * 1000 // Convert km to meters
        }
        return distance
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth's radius in km

        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)

        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return R * c
    }

    private fun updateButtonVisibility(order: Order) {
        // Hide all buttons initially
        btnAccept.visibility = View.GONE
        btnReject.visibility = View.GONE
        btnStartDelivery.visibility = View.GONE
        btnCompleteDelivery.visibility = View.GONE
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
                    btnReject.visibility = View.VISIBLE
                }
            }
            "Accepted" -> {
                if (isAssignedToMe) {
                    btnStartDelivery.visibility = View.VISIBLE
                    btnCancelDelivery.visibility = View.VISIBLE
                    btnContactCustomer.visibility = View.VISIBLE
                }
            }
            "In Progress" -> {
                if (isAssignedToMe) {
                    btnCompleteDelivery.visibility = View.VISIBLE
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
            val currentOrder = order ?: return@setOnClickListener

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Accept the order using Firebase Function or direct Firestore update
                    val result = DriverOrderRepository.acceptOrder(currentOrder.id)

                    CoroutineScope(Dispatchers.Main).launch {
                        if (result) {
                            Toast.makeText(this@OrderDetailActivity, "Order accepted", Toast.LENGTH_SHORT).show()
                            // Update local order object
                            currentOrder.driverUid = auth.currentUser?.uid
                            currentOrder.status = "Accepted"
                            displayOrderDetails(currentOrder)
                        } else {
                            Toast.makeText(this@OrderDetailActivity, "Failed to accept order", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error accepting order: ${e.message}")
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(this@OrderDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Reject order button
        btnReject.setOnClickListener {
            val currentOrder = order ?: return@setOnClickListener

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Reject the order using Firebase Function or direct Firestore update
                    val result = DriverOrderRepository.rejectOrder(currentOrder.id)

                    CoroutineScope(Dispatchers.Main).launch {
                        if (result) {
                            Toast.makeText(this@OrderDetailActivity, "Order rejected", Toast.LENGTH_SHORT).show()
                            finish() // Go back to the orders list
                        } else {
                            Toast.makeText(this@OrderDetailActivity, "Failed to reject order", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error rejecting order: ${e.message}")
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(this@OrderDetailActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Start delivery button (change status to In Progress)
        btnStartDelivery.setOnClickListener {
            val currentOrder = order ?: return@setOnClickListener

            DriverOrderRepository.updateOrderStatus(currentOrder.id, "In Progress", { success ->
                if (success) {
                    Toast.makeText(this, "Delivery started", Toast.LENGTH_SHORT).show()
                    // Update the order object and UI
                    currentOrder.status = "In Progress"
                    displayOrderDetails(currentOrder)
                } else {
                    Toast.makeText(this, "Failed to start delivery", Toast.LENGTH_SHORT).show()
                }
            })
        }

        // Complete delivery button
        btnCompleteDelivery.setOnClickListener {
            val currentOrder = order ?: return@setOnClickListener

            DriverOrderRepository.updateOrderStatus(currentOrder.id, "Delivered") { success ->
                if (success) {
                    Toast.makeText(this, "Delivery completed", Toast.LENGTH_SHORT).show()
                    // Update the order object and UI
                    currentOrder.status = "Delivered"
                    displayOrderDetails(currentOrder)
                } else {
                    Toast.makeText(this, "Failed to complete delivery", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Cancel delivery button
        btnCancelDelivery.setOnClickListener {
            showCancelConfirmation()
        }

        // Contact customer button
        btnContactCustomer.setOnClickListener {
            contactCustomer()
        }
    }

    private fun showCancelConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Delivery")
            .setMessage("Are you sure you want to cancel this delivery? This action cannot be undone.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                val currentOrder = order ?: return@setPositiveButton

                DriverOrderRepository.cancelOrderAssignment(currentOrder.id) { success ->
                    if (success) {
                        Toast.makeText(this, "Delivery cancelled", Toast.LENGTH_SHORT).show()
                        finish() // Return to the orders list
                    } else {
                        Toast.makeText(this, "Failed to cancel delivery", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun contactCustomer() {
        val currentOrder = order ?: return

        val driverUid = auth.currentUser?.uid ?: return
        val customerUid = currentOrder.uid
        val orderId = currentOrder.id

        ChatRepository.createOrGetChat(orderId, driverUid, customerUid) { chatId ->
            if (chatId != null) {
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("EXTRA_CHAT_ID", chatId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Failed to open chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        // Refresh order data if we have an ID
        orderId?.let { fetchOrderById(it) }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}