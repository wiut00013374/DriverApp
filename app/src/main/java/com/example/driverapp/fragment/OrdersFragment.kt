package com.example.driverapp.fragment

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.driverapp.R
import com.example.driverapp.adapters.OrderAdapter // Correct import
import com.example.driverapp.data.Order
import com.google.firebase.database.FirebaseDatabase

class OrdersFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: OrderAdapter
    private val ordersList = mutableListOf<Order>()
    private lateinit var database: FirebaseDatabase // Realtime Database

    companion object {
        private const val TAG = "DriverOrdersFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_orders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewOrders)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Corrected adapter instantiation:
        adapter = OrderAdapter(ordersList)
        recyclerView.adapter = adapter

        database = FirebaseDatabase.getInstance() // Initialize Realtime Database

        // We don't fetch orders here anymore.  The polling in DriverActivity handles it.
    }
}