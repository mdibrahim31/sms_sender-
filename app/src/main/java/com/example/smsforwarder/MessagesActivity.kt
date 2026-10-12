package com.example.smsforwarder

import android.net.Uri
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

    companion object {
        const val EXTRA_SENDER = "extra_sender"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_messages)

        val sender = intent.getStringExtra(EXTRA_SENDER) ?: "Unknown"

        val tvTitle = findViewById<TextView>(R.id.tvMessagesTitle)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewMessages)

        tvTitle.text = "Messages from $sender"
        recyclerView.layoutManager = LinearLayoutManager(this)

        val messages = loadMessagesForSender(sender)
        recyclerView.adapter = MessagesAdapter(messages)
    }

    private fun loadMessagesForSender(sender: String): List<SmsMessage> {
        val list = mutableListOf<SmsMessage>()
        try {
            val uri = Uri.parse("content://sms/inbox")
            val selection = "address = ?"
            val selectionArgs = arrayOf(sender)
            val cursor = contentResolver.query(uri, arrayOf("_id", "address", "body", "date"), selection, selectionArgs, "date DESC")

            cursor?.use {
                val idxAddress = it.getColumnIndex("address")
                val idxBody = it.getColumnIndex("body")
                val idxDate = it.getColumnIndex("date")
                val idxId = it.getColumnIndex("_id")

                while (it.moveToNext()) {
                    val address = if (idxAddress >= 0) it.getString(idxAddress) ?: sender else sender
                    val body = if (idxBody >= 0) it.getString(idxBody) ?: "" else ""
                    val date = if (idxDate >= 0) it.getLong(idxDate) else 0L
                    val id = if (idxId >= 0) it.getString(idxId) else "0"

                    list.add(SmsMessage(id, address, body, date))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
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
