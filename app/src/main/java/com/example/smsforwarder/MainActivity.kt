package com.example.smsforwarder

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etSupabaseUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var btnSave: Button
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        const val PREFS_NAME = "SmsForwarderPrefs"
        const val KEY_SUPABASE_URL = "supabase_url"
        const val KEY_API_KEY = "api_key"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etSupabaseUrl = findViewById(R.id.etSupabaseUrl)
        etApiKey = findViewById(R.id.etApiKey)
        btnSave = findViewById(R.id.btnSave)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load saved values
        etSupabaseUrl.setText(sharedPreferences.getString(KEY_SUPABASE_URL, ""))
        etApiKey.setText(sharedPreferences.getString(KEY_API_KEY, ""))

        btnSave.setOnClickListener {
            val url = etSupabaseUrl.text.toString().trim()
            val apiKey = etApiKey.text.toString().trim()

            if (url.isEmpty() || apiKey.isEmpty()) {
                Toast.makeText(this, "Please enter both URL and API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sharedPreferences.edit().apply {
                putString(KEY_SUPABASE_URL, url)
                putString(KEY_API_KEY, apiKey)
                apply()
            }

            Toast.makeText(this, "Configuration saved successfully!", Toast.LENGTH_SHORT).show()
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val receiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
        val readSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)

        val listPermissionsNeeded = mutableListOf<String>()
        if (receiveSms != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.RECEIVE_SMS)
        }
        if (readSms != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_SMS)
        }

        if (listPermissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "SMS permissions are required for this app to function.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
