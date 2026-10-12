package com.example.smsforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvAvailableServices: TextView
    private lateinit var etSearch: EditText
    private lateinit var tvActiveForwarding: TextView
    private lateinit var btnSendSelected: Button
    private lateinit var btnReload: Button
    private lateinit var recyclerViewSenders: RecyclerView

    private lateinit var smsAdapter: SmsAdapter

    private val allSenderGroups = mutableListOf<SenderGroup>()
    private val displayedSenderGroups = mutableListOf<SenderGroup>()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvAvailableServices = findViewById(R.id.tvAvailableServices)
        etSearch = findViewById(R.id.etSearch)
        tvActiveForwarding = findViewById(R.id.tvActiveForwarding)
        btnSendSelected = findViewById(R.id.btnSendSelected)
        btnReload = findViewById(R.id.btnReload)
        recyclerViewSenders = findViewById(R.id.recyclerViewSenders)

        recyclerViewSenders.layoutManager = LinearLayoutManager(this)
        smsAdapter = SmsAdapter(
            displayedSenderGroups,
            onToggleChanged = { _, _ -> updateActiveCount() },
            onViewMessages = { group ->
                SmsRepository.selectedGroup = group
                val intent = Intent(this, MessagesActivity::class.java)
                startActivity(intent)
            },
            onSendToDb = { group -> sendGroupToSupabase(group) }
        )
        recyclerViewSenders.adapter = smsAdapter

        btnReload.setOnClickListener { loadInboxSms() }
        btnSendSelected.setOnClickListener { sendSelectedToSupabase() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSenders(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val receiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
        val readSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)

        val needed = mutableListOf<String>()
        if (receiveSms != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.RECEIVE_SMS)
        if (readSms != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_SMS)

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            loadInboxSms()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            loadInboxSms()
        }
    }

    private fun loadInboxSms() {
        allSenderGroups.clear()
        val uri = Uri.parse("content://sms/inbox")
        val cursor = contentResolver.query(uri, arrayOf("_id", "address", "body", "date"), null, null, "date DESC")

        cursor?.use {
            val idxAddress = it.getColumnIndex("address")
            val idxBody = it.getColumnIndex("body")
            val idxDate = it.getColumnIndex("date")
            val idxId = it.getColumnIndex("_id")

            val map = mutableMapOf<String, MutableList<SmsMessage>>()

            while (it.moveToNext()) {
                val address = if (idxAddress >= 0) it.getString(idxAddress) ?: "Unknown" else "Unknown"
                val body = if (idxBody >= 0) it.getString(idxBody) ?: "" else ""
                val date = if (idxDate >= 0) it.getLong(idxDate) else 0L
                val id = if (idxId >= 0) it.getString(idxId) else "0"

                val msg = SmsMessage(id, address, body, date)
                map.getOrPut(address) { mutableListOf() }.add(msg)
            }

            for ((sender, messages) in map) {
                allSenderGroups.add(SenderGroup(sender, messages, false))
            }
        }

        filterSenders(etSearch.text.toString())
        Toast.makeText(this, "Loaded ${allSenderGroups.size} senders", Toast.LENGTH_SHORT).show()
    }

    private fun filterSenders(query: String) {
        displayedSenderGroups.clear()
        if (query.isEmpty()) {
            displayedSenderGroups.addAll(allSenderGroups)
        } else {
            val q = query.lowercase()
            for (group in allSenderGroups) {
                if (group.sender.lowercase().contains(q) || group.messages.any { it.body.lowercase().contains(q) }) {
                    displayedSenderGroups.add(group)
                }
            }
        }
        smsAdapter.notifyDataSetChanged()
        tvAvailableServices.text = "Available Services (${allSenderGroups.size})"
        updateActiveCount()
    }

    private fun updateActiveCount() {
        val activeCount = allSenderGroups.count { it.isEnabled }
        tvActiveForwarding.text = "Forwarding: $activeCount of ${allSenderGroups.size} services active"
    }

    private fun sendGroupToSupabase(group: SenderGroup) {
        val urlStr = BuildConfig.SUPABASE_URL
        val apiKey = BuildConfig.SUPABASE_API_KEY
        val userId = BuildConfig.VENDOR_USER_ID

        if (urlStr.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "Supabase URL/Key not configured in GitHub Secrets!", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Pushing ${group.messages.size} messages to sms_forwarder...", Toast.LENGTH_SHORT).show()

        thread {
            for (msg in group.messages) {
                try {
                    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("apikey", apiKey)
                        setRequestProperty("Authorization", "Bearer $apiKey")
                        setRequestProperty("Prefer", "return=minimal")
                        doOutput = true
                        connectTimeout = 10000
                    }
                    val txnId = SmsParser.extractTxnId(msg.body) ?: ""
                    val escapedBody = msg.body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
                    val json = """
                        {
                            "user_id": "$userId",
                            "txn_id": "$txnId",
                            "phone": "${msg.sender}",
                            "full_msg": "$escapedBody"
                        }
                    """.trimIndent()
                    OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(json); it.flush() }
                    conn.responseCode
                    conn.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            runOnUiThread {
                Toast.makeText(this, "Pushed messages for ${group.sender} successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendSelectedToSupabase() {
        val selected = allSenderGroups.filter { it.isEnabled }
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select services using toggles first.", Toast.LENGTH_SHORT).show()
            return
        }
        for (group in selected) {
            sendGroupToSupabase(group)
        }
    }
}
