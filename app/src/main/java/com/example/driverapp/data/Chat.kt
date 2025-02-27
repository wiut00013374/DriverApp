package com.example.driverapp.data

data class Chat(
    var id: String = "",               // Realtime Database key
    val orderId: String = "",
    val driverUid: String = "",
    val customerUid: String = "",
    var lastMessage: String? = null,   // For displaying in the list of chats
    var timestamp: Long = 0L
)