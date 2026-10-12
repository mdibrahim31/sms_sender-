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

        fun forwardMessage(context: Context, sender: String, messageBody: String) {
            val supabaseUrl = BuildConfig.SUPABASE_URL
            val apiKey = BuildConfig.SUPABASE_API_KEY
            val userId = BuildConfig.VENDOR_USER_ID

            if (supabaseUrl.isEmpty() || apiKey.isEmpty()) {
                Log.w(TAG, "Supabase URL or API Key is not configured via GitHub Secrets.")
                return
            }

            thread {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL(supabaseUrl)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("apikey", apiKey)
                        setRequestProperty("Authorization", "Bearer $apiKey")
                        setRequestProperty("Prefer", "return=minimal")
                        doOutput = true
                        connectTimeout = 15000
                        readTimeout = 15000
                    }

                    val txnId = SmsParser.extractTxnId(messageBody) ?: ""
                    val escapedBody = messageBody.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
                    val jsonInputString = """
                        {
                            "user_id": "$userId",
                            "txn_id": "$txnId",
                            "phone": "$sender",
                            "full_msg": "$escapedBody"
                        }
                    """.trimIndent()

                    OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                        writer.write(jsonInputString)
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        Log.i(TAG, "Successfully forwarded SMS to Supabase sms_forwarder. Response code: $responseCode")
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

                forwardMessage(context, sender, messageBody)
            }
        }
    }
}
