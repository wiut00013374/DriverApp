package com.example.driverapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.driverapp.adapters.MessageAdapter
import com.example.driverapp.data.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messagesList = mutableListOf<Message>()

    private lateinit var etMessageInput: EditText
    private lateinit var btnSendMessage: Button

    private var chatId: String? = null

    private val database by lazy { FirebaseDatabase.getInstance() }  // Realtime Database
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recyclerView = findViewById(R.id.recyclerViewChatMessages)
        etMessageInput = findViewById(R.id.etMessageInput)
        btnSendMessage = findViewById(R.id.btnSendMessage)

        adapter = MessageAdapter(messagesList)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        chatId = intent.getStringExtra("EXTRA_CHAT_ID") // Get chatId

        if (chatId == null) {
            Toast.makeText(this, "Invalid chat session", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        listenForMessages()

        btnSendMessage.setOnClickListener {
            sendMessage()
        }
    }

    private fun listenForMessages() {
        database.getReference("chats/$chatId/messages")
            .orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    messagesList.clear()
                    for (messageSnapshot in snapshot.children) {
                        val message = messageSnapshot.getValue(Message::class.java)
                        message?.let { messagesList.add(it) }
                    }
                    adapter.notifyDataSetChanged()
                    recyclerView.scrollToPosition(messagesList.size - 1) // Scroll to bottom
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ChatActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }


    // Inside ChatActivity.kt
    private fun sendMessage() {
        val messageText = etMessageInput.text.toString().trim()
        if (messageText.isEmpty()) {
            Toast.makeText(this, "Cannot send empty message", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUserId = auth.currentUser?.uid ?: return

        val message = Message(
            senderUid = currentUserId,
            text = messageText,
            timestamp = System.currentTimeMillis()
            // REMOVE: isRead = false  <-- This line caused the error
        )

        val messageId = database.getReference("chats/$chatId/messages").push().key // Generate unique ID
        if (messageId != null) {
            database.getReference("chats/$chatId/messages/$messageId").setValue(message)
                .addOnSuccessListener {
                    etMessageInput.text.clear()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}