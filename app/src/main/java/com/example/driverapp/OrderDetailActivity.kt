package com.example.driverapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.driverapp.data.Order
import com.example.driverapp.repos.DriverOrderRepository
import com.example.myapp.repos.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

class OrderDetailActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var tvOriginCity: TextView
    private lateinit var tvDestinationCity: TextView
    private lateinit var tvPrice: TextView
    private lateinit var tvTruckType: TextView
    private lateinit var tvVolume: TextView
    private lateinit var tvWeight: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnAccept: Button
    private lateinit var btnStartDelivery: Button
    private lateinit var btnCompleteDelivery: Button
    private lateinit var btnCancelDelivery: Button
    private lateinit var btnContactCustomer: Button

    private var order: Order? = null

    companion object {
        private const val TAG = "OrderDetailActivity"
    }

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
        tvTruckType = findViewById(R.id.tvDetailTruckType)
        tvVolume = findViewById(R.id.tvDetailVolume)
        tvWeight = findViewById(R.id.tvDetailWeight)
        tvStatus = findViewById(R.id.tvDetailStatus)

        btnAccept = findViewById(R.id.btnDetailAccept)
        btnStartDelivery = findViewById(R.id.btnDetailStartDelivery)
        btnCompleteDelivery = findViewById(R.id.btnDetailCompleteDelivery)
        btnCancelDelivery = findViewById(R.id.btnDetailCancelDelivery)
        btnContactCustomer = findViewById(R.id.btnDetailContactCustomer)

        // Get order from intent
        order = intent.getParcelableExtra("EXTRA_ORDER")
        if (order == null) {
            Toast.makeText(this, "Order data not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Display order details
        displayOrderDetails()

        // Set up map with origin and destination markers
        setupMap()

        // Set up button listeners
        setupButtonListeners()
    }

    private fun displayOrderDetails() {
        order?.let { order ->
            tvOriginCity.text = "Origin: ${order.originCity}"
            tvDestinationCity.text = "Destination: ${order.destinationCity}"
            tvPrice.text = "Price: $${String.format("%.2f", order.totalPrice)}"
            tvTruckType.text = "Truck Type: ${order.truckType}"
            tvVolume.text = "Volume: ${order.volume} m³"
            tvWeight.text = "Weight: ${order.weight} kg"
            tvStatus.text = "Status: ${order.status}"

            // Configure buttons based on order status
            updateButtonVisibility(order.status)
        }
    }

    private fun setupMap() {
        order?.let { order ->
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
            val polyline = Polyline(mapView)
            polyline.addPoint(originPoint)
            polyline.addPoint(destPoint)
            polyline.color = resources.getColor(R.color.purple_500, theme)
            polyline.width = 5.0f
            mapView.overlays.add(polyline)

            // Zoom to show both points
            mapView.zoomToBoundingBox(polyline.bounds.increaseByScale(1.5f), true)
            mapView.invalidate()
        }
    }

    private fun updateButtonVisibility(status: String) {
        // Hide all buttons initially
        btnAccept.visibility = Button.GONE
        btnStartDelivery.visibility = Button.GONE
        btnCompleteDelivery.visibility = Button.GONE
        btnCancelDelivery.visibility = Button.GONE

        // Always show contact customer button for active orders
        val currentDriverUid = FirebaseAuth.getInstance().currentUser?.uid
        val isAssignedToMe = order?.driverUid == currentDriverUid
        btnContactCustomer.visibility = if (isAssignedToMe) Button.VISIBLE else Button.GONE

        // Show buttons based on order status
        when (status) {
            "Pending" -> {
                btnAccept.visibility = Button.VISIBLE
                btnCancelDelivery.visibility = Button.GONE
            }
            "Accepted" -> {
                if (isAssignedToMe) {
                    btnStartDelivery.visibility = Button.VISIBLE
                    btnCancelDelivery.visibility = Button.VISIBLE
                }
            }
            "In Progress" -> {
                if (isAssignedToMe) {
                    btnCompleteDelivery.visibility = Button.VISIBLE
                    btnCancelDelivery.visibility = Button.VISIBLE
                }
            }
            "Completed", "Delivered" -> {
                // No action buttons for completed orders
            }
        }
    }

    private fun setupButtonListeners() {
        // Accept order
        btnAccept.setOnClickListener {
            order?.let { order ->
                DriverOrderRepository.acceptOrder(order.id) { success ->
                    if (success) {
                        Toast.makeText(this, "Order accepted successfully", Toast.LENGTH_SHORT).show()
                        // Update the order object and UI
                        order.status = "Accepted"
                        order.driverUid = FirebaseAuth.getInstance().currentUser?.uid
                        displayOrderDetails()
                    } else {
                        Toast.makeText(this, "Failed to accept order", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Start delivery (change status to In Progress)
        btnStartDelivery.setOnClickListener {
            order?.let { order ->
                DriverOrderRepository.updateOrderStatus(order.id, "In Progress") { success ->
                    if (success) {
                        Toast.makeText(this, "Delivery started", Toast.LENGTH_SHORT).show()
                        // Update the order object and UI
                        order.status = "In Progress"
                        displayOrderDetails()
                    } else {
                        Toast.makeText(this, "Failed to start delivery", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Complete delivery
        btnCompleteDelivery.setOnClickListener {
            order?.let { order ->
                DriverOrderRepository.updateOrderStatus(order.id, "Delivered") { success ->
                    if (success) {
                        Toast.makeText(this, "Delivery completed", Toast.LENGTH_SHORT).show()
                        // Update the order object and UI
                        order.status = "Delivered"
                        displayOrderDetails()
                    } else {
                        Toast.makeText(this, "Failed to complete delivery", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Cancel delivery
        btnCancelDelivery.setOnClickListener {
            showCancelConfirmation()
        }

        // Contact customer
        btnContactCustomer.setOnClickListener {
            contactCustomer()
        }
    }

    private fun showCancelConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Delivery")
            .setMessage("Are you sure you want to cancel this delivery? This action cannot be undone.")
            .setPositiveButton("Yes, Cancel") { _, _ ->
                order?.let { order ->
                    DriverOrderRepository.cancelOrderAssignment(order.id) { success ->
                        if (success) {
                            Toast.makeText(this, "Delivery cancelled", Toast.LENGTH_SHORT).show()
                            finish() // Return to the orders list
                        } else {
                            Toast.makeText(this, "Failed to cancel delivery", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun contactCustomer() {
        order?.let { order ->
            val driverUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val customerUid = order.uid
            val orderId = order.id

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