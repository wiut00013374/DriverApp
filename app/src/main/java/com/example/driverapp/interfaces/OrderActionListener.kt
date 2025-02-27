package com.example.driverapp.interfaces

import com.example.driverapp.data.Order

interface OrderActionListener {
    fun onOrderClick(order: Order)
    fun onAcceptOrder(order: Order)
    fun onContactCustomer(order: Order)
}