package com.example.smsforwarder

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SupabaseService {

    private const val TAG = "SupabaseService"

    fun getEndpointUrl(): String {
        val base = BuildConfig.SUPABASE_URL.trim()
        if (base.isEmpty()) return ""
        return if (base.endsWith("/rest/v1/sms_forwarder")) {
            base
        } else if (base.endsWith("/rest/v1")) {
            "$base/sms_forwarder"
        } else if (base.endsWith("/")) {
            "${base}rest/v1/sms_forwarder"
        } else {
            "$base/rest/v1/sms_forwarder"
        }
    }

    fun isConfigured(): Boolean {
        return getEndpointUrl().isNotEmpty() && BuildConfig.SUPABASE_API_KEY.isNotEmpty()
    }

    /**
     * Send a single message record to Supabase table `sms_forwarder`.
     * Returns true if successful (HTTP 200..299).
     */
    fun sendSms(sender: String, messageBody: String): Boolean {
        if (!isConfigured()) {
            Log.w(TAG, "Supabase URL or API Key is missing.")
            return false
        }

        var connection: HttpURLConnection? = null
        try {
            val endpoint = getEndpointUrl()
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("apikey", BuildConfig.SUPABASE_API_KEY)
                setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_API_KEY}")
                setRequestProperty("Prefer", "return=minimal")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
            }

            val userId = BuildConfig.VENDOR_USER_ID
            val txnId = SmsParser.extractTxnId(messageBody) ?: ""
            val escapedBody = messageBody
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t")

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
            val isSuccess = responseCode in 200..299
            if (isSuccess) {
                Log.i(TAG, "Sent SMS to Supabase successfully. Status: $responseCode")
            } else {
                Log.e(TAG, "Failed sending SMS to Supabase. HTTP code: $responseCode")
            }
            return isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS to Supabase", e)
            return false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Query existing messages from Supabase to check if a message has already been inserted.
     * Prevents duplicate insertion when rechecking missed messages.
     */
    fun fetchExistingFullMessages(sender: String): Set<String> {
        val set = mutableSetOf<String>()
        if (!isConfigured()) return set

        var connection: HttpURLConnection? = null
        try {
            val encodedPhone = URLEncoder.encode(sender, "UTF-8")
            val endpoint = "${getEndpointUrl()}?select=full_msg&phone=eq.$encodedPhone"
            val url = URL(endpoint)

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", BuildConfig.SUPABASE_API_KEY)
                setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_API_KEY}")
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (connection.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val response = reader.readText()
                reader.close()

                // Simple JSON array extraction for "full_msg":"..."
                val regex = Regex(""""full_msg"\s*:\s*"((?:\\.|[^"\\])*)"""")
                val matches = regex.findAll(response)
                for (match in matches) {
                    val raw = match.groupValues[1]
                    val unescaped = raw
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\\\", "\\")
                    set.add(unescaped)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching existing messages from Supabase for $sender", e)
        } finally {
            connection?.disconnect()
        }
        return set
    }
}
