package com.example.driverapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.driverapp.R
import com.example.driverapp.data.Order
import com.example.driverapp.interfaces.OrderActionListener
import com.google.firebase.auth.FirebaseAuth

class OrderAdapter(
    private val orders: List<Order>,
    private val listener: OrderActionListener
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOrigin: TextView = itemView.findViewById(R.id.tvOrigin)
        val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val btnAccept: Button = itemView.findViewById(R.id.btnAcceptOrder)
        val btnContact: Button = itemView.findViewById(R.id.btnContactCustomer)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onOrderClick(orders[position])
                }
            }

            btnAccept.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onAcceptOrder(orders[position])
                }
            }

            btnContact.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onContactCustomer(orders[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]

        holder.tvOrigin.text = "From: ${order.originCity}"
        holder.tvDestination.text = "To: ${order.destinationCity}"
        holder.tvPrice.text = "$${String.format("%.2f", order.totalPrice)}"
        holder.tvStatus.text = "Status: ${order.status}"

        // Show/hide buttons based on order status and assignment
        if (order.status == "Pending" && order.driverUid == null) {
            // Available for acceptance
            holder.btnAccept.visibility = View.VISIBLE
            holder.btnContact.visibility = View.GONE
        } else if (order.driverUid == currentUserId) {
            // Order is assigned to current driver
            holder.btnAccept.visibility = View.GONE
            holder.btnContact.visibility = View.VISIBLE
        } else {
            // Not relevant to this driver
            holder.btnAccept.visibility = View.GONE
            holder.btnContact.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = orders.size
}