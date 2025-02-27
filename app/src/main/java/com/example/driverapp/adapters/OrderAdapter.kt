package com.example.driverapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.driverapp.R
import com.example.driverapp.data.Order


class OrderAdapter(
    private val orders: List<Order>
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOrigin: TextView = itemView.findViewById(R.id.tvOrigin)
        val tvDestination: TextView = itemView.findViewById(R.id.tvDestination)
        val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus) // Add status TextView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false) // Use your order item layout
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]

        holder.tvOrigin.text = "From: ${order.originCity}"
        holder.tvDestination.text = "To: ${order.destinationCity}"
        holder.tvPrice.text = "$${String.format("%.2f", order.totalPrice)}"
        holder.tvStatus.text = "Status: ${order.status}" // Display status
    }

    override fun getItemCount(): Int = orders.size
}