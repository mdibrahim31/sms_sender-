package com.example.smsforwarder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private lateinit var btnSettings: Button
    private lateinit var recyclerViewSenders: RecyclerView

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var smsAdapter: SmsAdapter

    private val allSenderGroups = mutableListOf<SenderGroup>()
    private val displayedSenderGroups = mutableListOf<SenderGroup>()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        const val PREFS_NAME = "SmsForwarderPrefs"
        const val KEY_SUPABASE_URL = "supabase_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_VENDOR_USER_ID = "vendor_user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvAvailableServices = findViewById(R.id.tvAvailableServices)
        etSearch = findViewById(R.id.etSearch)
        tvActiveForwarding = findViewById(R.id.tvActiveForwarding)
        btnSendSelected = findViewById(R.id.btnSendSelected)
        btnReload = findViewById(R.id.btnReload)
        btnSettings = findViewById(R.id.btnSettings)
        recyclerViewSenders = findViewById(R.id.recyclerViewSenders)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        recyclerViewSenders.layoutManager = LinearLayoutManager(this)
        smsAdapter = SmsAdapter(
            displayedSenderGroups,
            onToggleChanged = { _, _ -> updateActiveCount() },
            onViewMessages = { group ->
                MessagesActivity.currentMessages = group.messages
                MessagesActivity.senderName = group.sender
                startActivity(Intent(this, MessagesActivity::class.java))
            },
            onSendToDb = { group -> sendGroupToSupabase(group) }
        )
        recyclerViewSenders.adapter = smsAdapter

        btnReload.setOnClickListener { loadInboxSms() }
        btnSendSelected.setOnClickListener { sendSelectedToSupabase() }
        btnSettings.setOnClickListener { showSettingsDialog() }

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
        val urlStr = sharedPreferences.getString(KEY_SUPABASE_URL, "")?.takeIf { it.isNotBlank() } ?: BuildConfig.SUPABASE_URL
        val apiKey = sharedPreferences.getString(KEY_API_KEY, "")?.takeIf { it.isNotBlank() } ?: BuildConfig.SUPABASE_API_KEY
        val userId = sharedPreferences.getString(KEY_VENDOR_USER_ID, "")?.takeIf { it.isNotBlank() } ?: BuildConfig.VENDOR_USER_ID

        if (urlStr.isEmpty() || apiKey.isEmpty()) {
            Toast.makeText(this, "Please configure Supabase URL & Key", Toast.LENGTH_LONG).show()
            showSettingsDialog()
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

    private fun showSettingsDialog() {
        val inputUrl = EditText(this).apply {
            hint = "Supabase Endpoint URL"
            setText(sharedPreferences.getString(KEY_SUPABASE_URL, BuildConfig.SUPABASE_URL))
        }
        val inputKey = EditText(this).apply {
            hint = "Supabase API Key"
            setText(sharedPreferences.getString(KEY_API_KEY, BuildConfig.SUPABASE_API_KEY))
        }
        val inputUserId = EditText(this).apply {
            hint = "Vendor User ID (for payment attribution)"
            setText(sharedPreferences.getString(KEY_VENDOR_USER_ID, BuildConfig.VENDOR_USER_ID))
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
            addView(inputUrl)
            addView(inputKey)
            addView(inputUserId)
        }

        AlertDialog.Builder(this)
            .setTitle("Supabase & Vendor Configuration")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                sharedPreferences.edit()
                    .putString(KEY_SUPABASE_URL, inputUrl.text.toString().trim())
                    .putString(KEY_API_KEY, inputKey.text.toString().trim())
                    .putString(KEY_VENDOR_USER_ID, inputUserId.text.toString().trim())
                    .apply()
                Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
