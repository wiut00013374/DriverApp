// adapters/ChatAdapter.kt
package com.example.driverapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.driverapp.R
import com.example.driverapp.data.Chat

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
        // You'll need to adapt this based on your Chat data class and layout.
        holder.tvDriverName.text = chat.driverUid // Or customerUid, depending on who you're showing
        holder.tvLastMessage.text = chat.lastMessage ?: "No messages yet"
        holder.tvTimestamp.text = chat.timestamp.toString() // Format this properly
    }

    override fun getItemCount(): Int = chats.size
}