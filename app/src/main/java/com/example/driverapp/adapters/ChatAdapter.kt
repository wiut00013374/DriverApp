// adapters/ChatAdapter.kt
package com.example.driverapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.driverapp.R
import com.example.driverapp.data.Chat
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(private val chats: List<Chat>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDriverName: TextView = itemView.findViewById(R.id.tvDriverName) // Update ID if needed
        val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage) // Update ID
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp) // Update ID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false) // Create this layout file
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        // Fetch customer/driver name using UID (pseudo-code)
        FirebaseFirestore.getInstance().collection("users").document(chat.customerUid)
            .get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("displayName") ?: "Customer"
                holder.tvDriverName.text = name
            }
        holder.tvLastMessage.text = chat.lastMessage ?: "No messages yet"
        holder.tvTimestamp.text = formatTimestamp(chat.timestamp)
    }

    private fun formatTimestamp(timestamp: Long): String {
        val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return format.format(Date(timestamp))
    }
    override fun getItemCount(): Int = chats.size
}