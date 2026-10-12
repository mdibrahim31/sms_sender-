package com.example.smsforwarder

import android.content.Context
import android.content.SharedPreferences

object ForwardingPreferences {

    private const val PREF_NAME = "sms_forwarding_prefs"
    private const val KEY_SELECTED_SENDERS = "selected_senders"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedSenders(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SELECTED_SENDERS, emptySet()) ?: emptySet()
    }

    fun isSenderSelected(context: Context, sender: String): Boolean {
        return getSelectedSenders(context).contains(sender)
    }

    fun setSenderSelected(context: Context, sender: String, isSelected: Boolean) {
        val current = getSelectedSenders(context).toMutableSet()
        if (isSelected) {
            current.add(sender)
        } else {
            current.remove(sender)
        }
        getPrefs(context).edit().putStringSet(KEY_SELECTED_SENDERS, current).apply()
    }

    fun setSelectedSenders(context: Context, senders: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_SELECTED_SENDERS, senders).apply()
    }
}
