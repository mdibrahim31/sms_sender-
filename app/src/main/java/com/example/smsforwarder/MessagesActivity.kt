package com.example.smsforwarder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessagesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        val tvTitle = findViewById<TextView>(R.id.tvMessagesTitle)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewMessages)

        val group = SmsRepository.selectedGroup
        val sender = group?.sender ?: "Unknown"
        val messages = group?.messages ?: emptyList()

        tvTitle.text = "Messages from $sender (${messages.size})"
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = MessagesAdapter(messages)
    }

    class MessagesAdapter(private val messages: List<SmsMessage>) :
        RecyclerView.Adapter<MessagesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvMsgBody: TextView = view.findViewById(R.id.tvMsgBody)
            val tvTxnId: TextView = view.findViewById(R.id.tvTxnId)
            val tvMsgDate: TextView = view.findViewById(R.id.tvMsgDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_detail, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val msg = messages[position]
            holder.tvMsgBody.text = msg.body

            val txnId = SmsParser.extractTxnId(msg.body)
            holder.tvTxnId.text = if (txnId != null) "TxnId: $txnId" else "TxnId: N/A"

            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            holder.tvMsgDate.text = sdf.format(Date(msg.date))
        }

        override fun getItemCount(): Int = messages.size
    }
}
