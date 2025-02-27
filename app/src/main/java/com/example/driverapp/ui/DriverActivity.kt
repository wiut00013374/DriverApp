package com.example.driverapp.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.driverapp.MainActivity
import com.example.driverapp.R
import com.example.driverapp.data.Order
import com.example.driverapp.login.DriverSignInActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class DriverActivity : AppCompatActivity() {

    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var availabilitySwitch: Switch
    private val handler = Handler(Looper.getMainLooper())
    private var lastNotificationTimestamp: Long = 0
    private val NOTIFICATION_COOLDOWN_MS = 10000 // 10 seconds cooldown


    // Polling interval (adjust as needed - 30 seconds is a reasonable starting point)
    private val POLLING_INTERVAL_MS = 30000L

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, start polling.
            startPolling()
        } else {
            // Permission denied.  Explain to the user.
            Toast.makeText(this, "Notification permission denied.  You will not receive order requests.", Toast.LENGTH_LONG).show()
            //  turn the switch OFF
            availabilitySwitch.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver)

        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()

        availabilitySwitch = findViewById(R.id.availabilitySwitch)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            updateAvailabilityUI() // Load initial state

            availabilitySwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // When turning ON, request permission if needed.
                    requestNotificationPermission() // This function handles the check and request
                } else {
                    // When turning OFF, stop polling.
                    stopPolling()
                    updateAvailabilityInDatabase(false) // Update the database
                }

            }
        } else { //added else statement
            // Not authenticated; redirect to DriverSignInActivity
            startActivity(Intent(this, DriverSignInActivity::class.java))
            finish()
            return
        }
        createNotificationChannel() // Create channel on activity creation

        //Check for notification, if exist, show dialog
        if (intent.hasExtra("orderId")) {
            val orderId = intent.getStringExtra("orderId")
            if (orderId != null) {
                fetchOrderDetailsAndShowDialog(orderId)
            }
        }
    }


    private fun updateAvailabilityUI() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in.", Toast.LENGTH_SHORT).show()
            return
        }
        database.getReference("drivers/${currentUser.uid}/available")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val available = snapshot.getValue(Boolean::class.java) ?: false
                    availabilitySwitch.isChecked = available
                    if (available) {
                        startPolling()
                    } else {
                        stopPolling()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@DriverActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private val pollingRunnable = object : Runnable {
        override fun run() {
            checkForNewOrders()
            handler.postDelayed(this, POLLING_INTERVAL_MS) // Schedule the next poll
        }
    }

    private fun startPolling() {
        handler.post(pollingRunnable) // Start immediately
    }

    private fun stopPolling() {
        handler.removeCallbacks(pollingRunnable) // Stop any pending polls
    }
    private fun updateAvailabilityInDatabase(isAvailable: Boolean) {
        val currentUser = auth.currentUser ?: return
        database.getReference("drivers/${currentUser.uid}/available")
            .setValue(isAvailable)
            .addOnSuccessListener {
                val message = if (isAvailable) "Available for orders" else "Not available for orders"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update availability: ${it.message}", Toast.LENGTH_SHORT).show()
                availabilitySwitch.isChecked = !isAvailable  //Important: revert switch state
            }
    }
    private fun checkForNewOrders() {
        val driverId = auth.currentUser?.uid ?: return
        database.getReference("orders")
            .orderByChild("status")
            .equalTo("pending") // Check for pending orders
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (orderSnapshot in snapshot.children) {
                        val order = orderSnapshot.getValue(Order::class.java)
                        if (order != null && order.driversContacted.containsKey(driverId) && order.driversContacted[driverId] == "pending") {
                            // This order is for this driver and is pending.
                            val currentTime = System.currentTimeMillis()
                            if(currentTime - lastNotificationTimestamp >= NOTIFICATION_COOLDOWN_MS){
                                showOrderNotification(order.id, order)
                                lastNotificationTimestamp = currentTime
                            }

                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("DriverActivity", "Error checking for new orders: ${error.message}")
                }
            })
    }

    private fun showOrderNotification(orderId: String, order: Order) {
        val intent = Intent(this, DriverActivity::class.java).apply { //Reopen the activity.
            putExtra("orderId", orderId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, "CHANNEL_ID") // "CHANNEL_ID"
            .setSmallIcon(R.drawable.ic_notification) //  your notification icon
            .setContentTitle("New Order Request")
            .setContentText("From: ${order.originCity}, To: ${order.destinationCity}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (ContextCompat.checkSelfPermission(this@DriverActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            notify(orderId.hashCode(), builder.build()) // Use orderId as unique ID
        }
        // showAcceptRejectDialog(orderId, order); //Show dialog after notification REMOVED FROM HERE
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "CHANNEL_ID"
            val channelName = "Order Notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance)
            channel.description = "Notifications for new order requests"

            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showAcceptRejectDialog(orderId: String, order: Order) {
        Handler(Looper.getMainLooper()).post {
            val dialog = AlertDialog.Builder(this)
                .setTitle("New Order Request")
                .setMessage("From: ${order.originCity}, To: ${order.destinationCity}\nDo you want to accept this order?")
                .setPositiveButton("Accept") { _, _ -> acceptOrder(orderId) }
                .setNegativeButton("Reject") { _, _ -> rejectOrder(orderId) }
                .setCancelable(false)
                .create()

            //The code below shows the dialog on top of the lock screen and other apps.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
            }
            dialog.show()
        }
    }
    private fun fetchOrderDetailsAndShowDialog(orderId: String) {
        database.getReference("orders/$orderId").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val order = snapshot.getValue(Order::class.java)
                if (order != null) {
                    showAcceptRejectDialog(orderId, order) // Now it's shown from here.
                } else {
                    Log.e("DriverActivity", "Order not found for ID: $orderId")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DriverActivity", "Error fetching order details: ${error.message}")
            }
        })
    }

    private fun acceptOrder(orderId: String) {
        val driverId = auth.currentUser?.uid
        if(driverId == null){
            Toast.makeText(this, "You must be logged in to accept orders.", Toast.LENGTH_SHORT).show()
            return
        }
        database.getReference("orders/$orderId/driversContacted/$driverId").setValue("accepted")
            .addOnSuccessListener{
                Toast.makeText(this, "Order accepted!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to accept order: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rejectOrder(orderId: String) {
        val driverId = auth.currentUser?.uid
        if(driverId == null){
            Toast.makeText(this, "You must be logged in to reject orders.", Toast.LENGTH_SHORT).show()
            return
        }
        database.getReference("orders/$orderId/driversContacted/$driverId").setValue("rejected")
            .addOnSuccessListener{
                Toast.makeText(this, "Order rejected.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to reject order: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onStop() {
        super.onStop()
        stopPolling() // VERY IMPORTANT: Stop polling when activity is not visible.
    }
    override fun onResume() {
        super.onResume()
        if (auth.currentUser != null && availabilitySwitch.isChecked) {
            startPolling() // Resume polling when activity becomes visible again and is available
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            // You already have permission
            startPolling() //Start if you already gave permission before.
            updateAvailabilityInDatabase(true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Request the permission using the ActivityResultLauncher
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        else{
            //Old versions does not require run time permission.
            startPolling()
            updateAvailabilityInDatabase(true)
        }
    }
}