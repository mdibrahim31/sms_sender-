package com.example.smsforwarder

data class SmsMessage(
    val id: String,
    val sender: String,
    val body: String,
    val date: Long
)

data class SenderGroup(
    val sender: String,
    val messages: MutableList<SmsMessage>,
    var isEnabled: Boolean = false
)
