package com.example.smsforwarder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class SmsAdapter(
    private var senderGroups: List<SenderGroup>,
    private val onToggleChanged: (SenderGroup, Boolean) -> Unit,
    private val onViewMessages: (SenderGroup) -> Unit,
    private val onSendToDb: (SenderGroup) -> Unit
) : RecyclerView.Adapter<SmsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvMsgCountBadge: TextView = view.findViewById(R.id.tvMsgCountBadge)
        val switchForward: SwitchCompat = view.findViewById(R.id.switchForward)
        val tvMessagePreview: TextView = view.findViewById(R.id.tvMessagePreview)
        val btnViewMessages: Button = view.findViewById(R.id.btnViewMessages)
        val btnSendToDb: Button = view.findViewById(R.id.btnSendToDb)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_sender, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            val group = senderGroups.getOrNull(position) ?: return

            holder.tvSender.text = group.sender ?: "Unknown"
            val count = group.messages.size
            holder.tvMsgCountBadge.text = "$count msg${if (count > 1) "s" else ""}"

            val latestMessage = group.messages.maxByOrNull { it.date }?.body ?: ""
            holder.tvMessagePreview.text = latestMessage

            // Avoid infinite triggering during recycling
            holder.switchForward.setOnCheckedChangeListener(null)
            holder.switchForward.isChecked = group.isEnabled

            holder.switchForward.setOnCheckedChangeListener { _, isChecked ->
                try {
                    group.isEnabled = isChecked
                    onToggleChanged(group, isChecked)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            holder.btnViewMessages.setOnClickListener {
                try {
                    val adapterPos = holder.adapterPosition
                    if (adapterPos != RecyclerView.NO_POSITION) {
                        val currentGroup = senderGroups.getOrNull(adapterPos)
                        if (currentGroup != null) {
                            onViewMessages(currentGroup)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            holder.btnSendToDb.setOnClickListener {
                try {
                    val adapterPos = holder.adapterPosition
                    if (adapterPos != RecyclerView.NO_POSITION) {
                        val currentGroup = senderGroups.getOrNull(adapterPos)
                        if (currentGroup != null) {
                            onSendToDb(currentGroup)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int = senderGroups.size

    fun updateData(newGroups: List<SenderGroup>) {
        senderGroups = newGroups
        notifyDataSetChanged()
    }
}
