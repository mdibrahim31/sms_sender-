package com.example.smsforwarder

import java.util.regex.Pattern

object SmsParser {
    private val txnPatterns = listOf(
        Pattern.compile("(?i)(?:TrxID|TxnId|Trx ID|Txn ID|Transaction ID|Trx|TrxNo)[:\\s#]*([A-Za-z0-9]+)"),
        Pattern.compile("(?i)(?:bKash|Nagad|Rocket|Upay|CellFin|Cash[\\s]*Out|Send[\\s]*Money|Payment)[^\\.]{0,30}?(?:Trx|Txn|ID)[:\\s#]*([A-Za-z0-9]+)")
    )

    fun extractTxnId(messageBody: String): String? {
        for (pattern in txnPatterns) {
            val matcher = pattern.matcher(messageBody)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
}
