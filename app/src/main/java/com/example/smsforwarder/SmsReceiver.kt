package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import kotlin.concurrent.thread

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (!messages.isNullOrEmpty()) {
                val sender = messages[0].displayOriginatingAddress ?: "Unknown"
                val bodyBuilder = StringBuilder()
                for (sms in messages) {
                    bodyBuilder.append(sms.messageBody ?: "")
                }
                val fullBody = bodyBuilder.toString()

                // Check if this sender is one of the chosen/active senders
                val isSelected = ForwardingPreferences.isSenderSelected(context, sender)
                if (!isSelected) {
                    Log.d(TAG, "Sender $sender is not selected for forwarding. Skipping.")
                    return
                }

                // Acquire a temporary WakeLock to guarantee execution even if device is in doze mode
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SMSForwarder:WakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire(20000) // 20 seconds maximum
                }

                val pendingResult = goAsync()
                thread {
                    try {
                        Log.i(TAG, "Incoming SMS from chosen sender $sender. Forwarding to Supabase...")
                        val success = SupabaseService.sendSms(sender, fullBody)
                        if (success) {
                            Log.i(TAG, "Successfully forwarded real-time SMS from $sender")
                        } else {
                            Log.e(TAG, "Failed forwarding real-time SMS from $sender")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception during real-time SMS forwarding", e)
                    } finally {
                        try {
                            wakeLock?.release()
                        } catch (e: Exception) {
                            // Ignored
                        }
                        pendingResult.finish()
                    }
                }
            }
        } else if (action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Device rebooted. SMS Forwarder receiver is ready.")
        }
    }
}
