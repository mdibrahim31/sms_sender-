package com.example.smsforwarder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MessagesActivity : AppCompatActivity() {

    private lateinit var adapter: MessagesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_messages)

            val btnBack = findViewById<ImageView>(R.id.btnBack)
            val tvTitle = findViewById<TextView>(R.id.tvMessagesTitle)
            val btnPushAll = findViewById<Button>(R.id.btnPushAllMessages)
            val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewMessages)

            btnBack?.setOnClickListener {
                finish()
            }

            val group = SmsRepository.selectedGroup
            val sender = group?.sender ?: "Unknown"
            val messages = group?.messages ?: emptyList()

            tvTitle?.text = "Messages from $sender (${messages.size})"
            recyclerView?.layoutManager = LinearLayoutManager(this)

            adapter = MessagesAdapter(messages) { msg ->
                pushSingleMessageToDb(msg)
            }
            recyclerView?.adapter = adapter

            btnPushAll?.setOnClickListener {
                pushAllMessagesToDb(sender, messages)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    private fun pushSingleMessageToDb(msg: SmsMessage) {
        if (!SupabaseService.isConfigured()) {
            Toast.makeText(this, "Supabase credentials missing in build configuration", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Pushing message to Supabase...", Toast.LENGTH_SHORT).show()
        thread {
            val success = SupabaseService.sendSms(msg.sender, msg.body)
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Message successfully inserted into sms_forwarder!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to insert message into Supabase", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pushAllMessagesToDb(sender: String, messages: List<SmsMessage>) {
        if (!SupabaseService.isConfigured()) {
            Toast.makeText(this, "Supabase credentials missing in build configuration", Toast.LENGTH_LONG).show()
            return
        }

        if (messages.isEmpty()) {
            Toast.makeText(this, "No messages to push", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Pushing ${messages.size} messages to Supabase...", Toast.LENGTH_SHORT).show()
        thread {
            var count = 0
            for (msg in messages) {
                if (SupabaseService.sendSms(msg.sender, msg.body)) {
                    count++
                }
            }
            runOnUiThread {
                Toast.makeText(this, "Inserted $count of ${messages.size} messages into sms_forwarder!", Toast.LENGTH_LONG).show()
            }
        }
    }

    class MessagesAdapter(
        private val messages: List<SmsMessage>,
        private val onPushClicked: (SmsMessage) -> Unit
    ) : RecyclerView.Adapter<MessagesAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvMsgBody: TextView = view.findViewById(R.id.tvMsgBody)
            val tvTxnId: TextView = view.findViewById(R.id.tvTxnId)
            val tvMsgDate: TextView = view.findViewById(R.id.tvMsgDate)
            val btnPushSingleMsg: Button = view.findViewById(R.id.btnPushSingleMsg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_message_detail, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            try {
                val msg = messages.getOrNull(position) ?: return
                holder.tvMsgBody.text = msg.body ?: ""

                val txnId = try {
                    SmsParser.extractTxnId(msg.body ?: "")
                } catch (e: Exception) {
                    null
                }
                holder.tvTxnId.text = if (!txnId.isNullOrEmpty()) "TxnId: $txnId" else "TxnId: N/A"

                val dateVal = msg.date
                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                holder.tvMsgDate.text = sdf.format(Date(dateVal))

                holder.btnPushSingleMsg.setOnClickListener {
                    onPushClicked(msg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun getItemCount(): Int = messages.size
    }
}
