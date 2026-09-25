package com.buyless.app.share

import com.buyless.app.util.Money

/**
 * Cleans up phone numbers typed in any style into the digits-only international form WhatsApp wants.
 * Malaysia is the default country, so a local number like "012-345 6789" becomes "60123456789".
 */
object PhoneNumbers {

    const val DEFAULT_COUNTRY = "60"

    /** Returns digits with country code, or null when it cannot be a real mobile number. */
    fun normalize(raw: String?, country: String = DEFAULT_COUNTRY): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        var digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        digits = when {
            hasPlus -> digits                                  // +65 9123 4567: already international
            digits.startsWith("00") -> digits.drop(2)          // 0060...: international dialling prefix
            digits.startsWith(country) && digits.length >= 10 -> digits // 6012...: already has the code
            digits.startsWith("0") -> country + digits.drop(1) // 012...: local, add Malaysia
            digits.startsWith("1") && digits.length in 9..10 -> country + digits // 12 345 6789: local without the 0
            else -> digits
        }
        // E.164 allows up to 15 digits; below 10 is too short to be a mobile number anywhere we care about.
        return digits.takeIf { it.length in 10..15 }
    }

    /** "60123456789" -> "+60 12-345 6789" for display. Other countries are shown as +digits. */
    fun pretty(normalized: String?): String? {
        if (normalized == null) return null
        if (!normalized.startsWith(DEFAULT_COUNTRY)) return "+$normalized"
        val local = normalized.drop(DEFAULT_COUNTRY.length)
        return when (local.length) {
            9 -> "+60 ${local.take(2)}-${local.substring(2, 5)} ${local.drop(5)}"
            10 -> "+60 ${local.take(2)}-${local.substring(2, 6)} ${local.drop(6)}"
            else -> "+$normalized"
        }
    }
}

/**
 * Writes the WhatsApp messages that go with the pay card. Pure text so it is unit tested; the wording
 * is friendly and short because it lands in a personal chat, and every number matches the Totals screen.
 */
object PayMessage {

    /** One of the person's items. shareSen is their part, splitWays is how many people shared it. */
    data class Line(val name: String, val shareSen: Long, val splitWays: Int)

    /** One row in the group summary. */
    data class GroupRow(val name: String, val amountSen: Long, val paid: Boolean)

    fun forPerson(
        name: String,
        merchant: String?,
        dateText: String,
        totalSen: Long,
        lines: List<Line>,
        extrasSen: Long,
        qrLabel: String?,
    ): String {
        val place = merchant?.let { " for $it" } ?: ""
        val sb = StringBuilder()
        sb.append("Hi ${firstName(name)}! Your share$place ($dateText) is ${Money.format(totalSen)}.\n\n")
        for (line in lines) {
            val label = when (line.splitWays) {
                1 -> line.name
                2 -> "half of ${line.name}"
                else -> "${line.name} (shared by ${line.splitWays})"
            }
            sb.append("- ").append(label).append(": ").append(Money.format(line.shareSen)).append('\n')
        }
        if (extrasSen != 0L) {
            val word = if (extrasSen > 0) "Tax, service and rounding" else "Your part of the discount"
            sb.append("- ").append(word).append(": ").append(if (extrasSen < 0) "-" else "").append(Money.format(extrasSen)).append('\n')
        }
        sb.append('\n').append(payLine(qrLabel)).append(" Thanks!")
        return sb.toString()
    }

    fun forGroup(merchant: String?, dateText: String, rows: List<GroupRow>, qrLabel: String?): String {
        val place = merchant ?: "the bill"
        val sb = StringBuilder("Split for $place ($dateText):\n\n")
        for (row in rows) {
            sb.append("- ").append(row.name).append(": ").append(Money.format(row.amountSen))
            if (row.paid) sb.append(" (paid)")
            sb.append('\n')
        }
        sb.append('\n').append(payLine(qrLabel)).append(" Thanks all!")
        return sb.toString()
    }

    private fun payLine(qrLabel: String?): String =
        if (qrLabel == null) "Please transfer when you can." else "Scan the QR in the picture to pay ($qrLabel)."

    /** "Aina Sofea" reads as "Aina" in a greeting. */
    private fun firstName(name: String) = name.trim().substringBefore(' ').ifEmpty { name }
}

/** Search for the people drawer: matches any word of the name from its start, or digits of the phone. */
object FriendSearch {
    fun matches(name: String, phone: String?, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        if (name.lowercase().split(' ', '-', '.').any { it.startsWith(q) } || name.lowercase().startsWith(q)) return true
        val qDigits = q.filter { it.isDigit() }
        return qDigits.length >= 3 && phone != null && phone.contains(qDigits.trimStart('0'))
    }

    /** Case and spacing do not make a different person: "aina " and "Aina" are the same friend. */
    fun key(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")
}
