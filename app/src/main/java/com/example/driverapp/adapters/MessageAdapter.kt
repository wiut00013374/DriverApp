// adapters/MessageAdapter.kt
package com.example.driverapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.driverapp.R
import com.example.driverapp.data.Message
import com.google.firebase.auth.FirebaseAuth

class MessageAdapter(
    private val messages: List<Message>
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // We need two different ViewHolder types for sent vs received messages
    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(message: Message) {
            // To be implemented by subclasses
        }
    }

    inner class SentMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.tvMessageText)
        private val timestamp: TextView = itemView.findViewById(R.id.tvMessageTimestamp)

        override fun bind(message: Message) {
            messageText.text = message.text
            // Format timestamp
            val date = java.util.Date(message.timestamp)
            val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            timestamp.text = format.format(date)
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : MessageViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.tvMessageText)
        private val timestamp: TextView = itemView.findViewById(R.id.tvMessageTimestamp)

        override fun bind(message: Message) {
            messageText.text = message.text
            // Format timestamp
            val date = java.util.Date(message.timestamp)
            val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            timestamp.text = format.format(date)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.senderUid == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }
}