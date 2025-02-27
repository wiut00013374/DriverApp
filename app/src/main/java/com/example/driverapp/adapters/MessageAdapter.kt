package com.example.driverapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.driverapp.R
import com.example.driverapp.data.Message
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(private val messages: List<Message>) :
    RecyclerView.Adapter<MessageAdapter.BaseMessageViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    abstract class BaseMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(message: Message)
        protected fun formatTimestamp(timestamp: Long) =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    inner class SentMessageViewHolder(itemView: View) : BaseMessageViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessageText)
        private val tvTime: TextView = itemView.findViewById(R.id.tvMessageTimestamp)

        override fun bind(message: Message) {
            tvMessage.text = message.text
            tvTime.text = formatTimestamp(message.timestamp)
        }
    }

    inner class ReceivedMessageViewHolder(itemView: View) : BaseMessageViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessageText)
        private val tvTime: TextView = itemView.findViewById(R.id.tvMessageTimestamp)

        override fun bind(message: Message) {
            tvMessage.text = message.text
            tvTime.text = formatTimestamp(message.timestamp)
        }
    }

    // Rest of the adapter remains unchanged
    override fun getItemViewType(position: Int) =
        if (messages[position].senderUid == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        VIEW_TYPE_SENT -> SentMessageViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_sent, parent, false)
        )
        else -> ReceivedMessageViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_received, parent, false)
        )
    }

    override fun onBindViewHolder(holder: BaseMessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }
}