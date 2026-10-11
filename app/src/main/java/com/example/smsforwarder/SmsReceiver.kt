package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages != null && messages.isNotEmpty()) {
                val sender = messages[0].displayOriginatingAddress ?: "Unknown"
                val fullBody = StringBuilder()
                for (sms in messages) {
                    fullBody.append(sms.messageBody ?: "")
                }
                val messageBody = fullBody.toString()

                val sharedPrefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
                val supabaseUrl = sharedPrefs.getString(MainActivity.KEY_SUPABASE_URL, "")
                val apiKey = sharedPrefs.getString(MainActivity.KEY_API_KEY, "")

                if (!supabaseUrl.isNullOrEmpty() && !apiKey.isNullOrEmpty()) {
                    forwardToSupabase(supabaseUrl, apiKey, sender, messageBody)
                } else {
                    Log.w(TAG, "Supabase URL or API Key is not configured.")
                }
            }
        }
    }

    private fun forwardToSupabase(endpointUrl: String, apiKey: String, sender: String, message: String) {
        thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(endpointUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("apikey", apiKey)
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 15000
                }

                val jsonInputString = "{\"sender\":\"$sender\",\"message\":\"$message\"}"

                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(jsonInputString)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    Log.i(TAG, "Successfully forwarded SMS to Supabase. Response code: $responseCode")
                } else {
                    Log.e(TAG, "Failed to forward SMS. Response code: $responseCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error forwarding SMS to Supabase", e)
            } finally {
                connection?.disconnect()
            }
        }
    }
}
