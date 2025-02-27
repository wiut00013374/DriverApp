package com.example.driverapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.driverapp.data.Order
import com.example.driverapp.repos.OrderRepository
import com.example.myapp.repos.ChatRepository
import com.google.firebase.auth.FirebaseAuth

class OrderDetailActivity : AppCompatActivity() {

    private var order: Order? = null

    companion object {
        private const val TAG = "OrderDetailsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_detail)

        // Get the order from intent
        order = intent.getParcelableExtra("EXTRA_ORDER") // if order implements Parcelable
        if (order == null) {
            Toast.makeText(this, "No order data!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Reference UI
        val tvOrigin = findViewById<TextView>(R.id.tvOriginDetail)
        val tvDestination = findViewById<TextView>(R.id.tvDestinationDetail)
        val tvPrice = findViewById<TextView>(R.id.tvPriceDetail)
        val tvVolume = findViewById<TextView>(R.id.tvVolumeDetail)
        val tvWeight = findViewById<TextView>(R.id.tvWeightDetail)

        val btnAccept = findViewById<Button>(R.id.btnAcceptOrder)
        val btnContact = findViewById<Button>(R.id.btnContactCustomer)

        // Populate fields
        tvOrigin.text = "Origin: ${order!!.originCity}"
        tvDestination.text = "Destination: ${order!!.destinationCity}"
        tvPrice.text = "Price: $${String.format("%.2f", order!!.totalPrice)}"
        tvVolume.text = "Volume: ${order!!.volume}"
        tvWeight.text = "Weight: ${order!!.weight}"

        // Accept button
        btnAccept.setOnClickListener {
            acceptOrder()
        }

        // Contact button
        btnContact.setOnClickListener {
            contactCustomer()
        }
    }

    private fun acceptOrder() {
        val currentDriverUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val orderId = order?.id ?: return
        OrderRepository.assignDriverToOrder(orderId, currentDriverUid) { success ->
            if (success) {
                Toast.makeText(this, "Order accepted!", Toast.LENGTH_SHORT).show()
                // Maybe finish this activity or refresh
            } else {
                Toast.makeText(this, "Failed to accept order", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun contactCustomer() {
        val currentDriverUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val customerUid = order?.uid
        val orderId = order?.id
        if (customerUid.isNullOrEmpty() || orderId.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid order or customer info!", Toast.LENGTH_SHORT).show()
            return
        }

        // Create or get the chat
        ChatRepository.createOrGetChat(orderId, currentDriverUid, customerUid) { chatId ->
            if (chatId != null) {
                // Navigate to a ChatActivity or ChatsFragment
                // For example:
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("EXTRA_CHAT_ID", chatId)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Failed to open chat", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
