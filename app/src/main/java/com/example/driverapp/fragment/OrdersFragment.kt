package com.example.driverapp.fragment

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.driverapp.OrderDetailActivity
import com.example.driverapp.R
import com.example.driverapp.adapters.OrderAdapter
import com.example.driverapp.data.Driver
import com.example.driverapp.data.Order
import com.example.driverapp.interfaces.OrderActionListener
import com.example.driverapp.repos.DriverOrderRepository
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class OrdersFragment : Fragment(), OrderActionListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tabLayout: TabLayout
    private lateinit var adapter: OrderAdapter

    private val availableOrdersList = mutableListOf<Order>()
    private val myOrdersList = mutableListOf<Order>()
    private var currentList = mutableListOf<Order>()

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var truckType = "Medium" // Default, will be updated when driver profile loads

    companion object {
        private const val TAB_AVAILABLE = 0
        private const val TAB_MY_ORDERS = 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_orders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewOrders)
        tabLayout = view.findViewById(R.id.tabLayoutOrders)

        // Set up tabs
        tabLayout.addTab(tabLayout.newTab().setText("Available Orders"))
        tabLayout.addTab(tabLayout.newTab().setText("My Orders"))

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = OrderAdapter(currentList, this)
        recyclerView.adapter = adapter

        // Load driver profile to get truck type
        loadDriverProfile()

        // Initial load of available orders
        currentList = availableOrdersList

        // Set up tab selection listener
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    TAB_AVAILABLE -> {
                        currentList = availableOrdersList
                        adapter = OrderAdapter(currentList, this@OrdersFragment)
                        recyclerView.adapter = adapter
                    }
                    TAB_MY_ORDERS -> {
                        currentList = myOrdersList
                        adapter = OrderAdapter(currentList, this@OrdersFragment)
                        recyclerView.adapter = adapter
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun loadDriverProfile() {
        val driverId = auth.currentUser?.uid ?: return

        firestore.collection("drivers").document(driverId)
            .get()
            .addOnSuccessListener { document ->
                val driver = document.toObject(Driver::class.java)
                if (driver != null) {
                    truckType = driver.truckType

                    // Now that we have the truck type, load orders
                    loadOrders()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()

                // Load orders with default truck type anyway
                loadOrders()
            }
    }

    private fun loadOrders() {
        // Listen for available orders
        DriverOrderRepository.listenForAvailableOrders(truckType) { orders ->
            availableOrdersList.clear()
            availableOrdersList.addAll(orders)

            // Update the adapter if currently showing available orders
            if (tabLayout.selectedTabPosition == TAB_AVAILABLE) {
                adapter.notifyDataSetChanged()
            }
        }

        // Listen for orders assigned to this driver
        DriverOrderRepository.listenForDriverOrders { orders ->
            myOrdersList.clear()
            myOrdersList.addAll(orders)

            // Update the adapter if currently showing my orders
            if (tabLayout.selectedTabPosition == TAB_MY_ORDERS) {
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onOrderClick(order: Order) {
        // Navigate to order details
        val intent = Intent(requireContext(), OrderDetailActivity::class.java)
        intent.putExtra("EXTRA_ORDER", order)
        startActivity(intent)
    }

    override fun onAcceptOrder(order: Order) {
        DriverOrderRepository.acceptOrder(order.id) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Order accepted successfully", Toast.LENGTH_SHORT).show()

                // Switch to "My Orders" tab
                tabLayout.getTabAt(TAB_MY_ORDERS)?.select()
            } else {
                Toast.makeText(requireContext(), "Failed to accept order", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onContactCustomer(order: Order) {
        // This is implemented in OrderDetailActivity
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove listeners to prevent memory leaks
        DriverOrderRepository.removeListeners()
    }
}