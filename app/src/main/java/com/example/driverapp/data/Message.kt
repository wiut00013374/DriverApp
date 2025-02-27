package com.example.driverapp.data

data class Message(
    var id: String = "",           // Realtime database id
    val chatId: String = "",       // ID of the chat
    val senderUid: String = "",    // who sent it
    val text: String = "",         // message text
    val timestamp: Long = System.currentTimeMillis(),
)