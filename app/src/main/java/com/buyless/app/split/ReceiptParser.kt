package com.buyless.app.split

import com.buyless.app.util.Money

/** One line of text from OCR with its position on the photo, in pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/** An item on the bill. priceSen is the line total (quantity already included). */
data class ReceiptItem(val name: String, val qty: Int, val priceSen: Long, val note: String? = null)

/** Everything read off a receipt. Charges are kept apart from items so they can be shared fairly. */
data class ParsedReceipt(
    val items: List<ReceiptItem>,
    val serviceSen: Long = 0,
    val taxSen: Long = 0,
    val roundingSen: Long = 0,
    val discountSen: Long = 0,
    val totalSen: Long? = null,
    val merchant: String? = null,
    /** True when the item prices already contain the service charge, so it is shown but not added again. */
    val serviceIncluded: Boolean = false,
    /** True when the item prices already contain SST ("Total Incl. 6% SST"), so it is not added again. */
    val taxIncluded: Boolean = false,
) {
    /** How far our sum is from the printed total, in sen. Null when no total was printed. */
    val mismatchSen: Long? get() = totalSen?.let { kotlin.math.abs(sumSen - it) }

    val itemsSen: Long get() = items.sumOf { it.priceSen }

    /** What the bill comes to by our reading: items plus only the charges that sit on top of them. */
    val sumSen: Long get() = itemsSen + extrasSen

    /** Charges added on top of the items. Included service or tax is skipped, it is already in the prices. */
    val extrasSen: Long
        get() = (if (serviceIncluded) 0L else serviceSen) + (if (taxIncluded) 0L else taxSen) + roundingSen - discountSen
}

/**
 * Turns OCR output into items and charges. Pure Kotlin, so it is unit tested on a PC.
 *
 * Why rows first: ML Kit often returns a receipt as two columns (all names in one block, all prices in
 * another). Re-joining lines that sit at the same height rebuilds "NASI LEMAK ... 12.00" rows, which is
 * how a person reads a receipt.
 */
object ReceiptParser {

    private val opts = setOf(RegexOption.IGNORE_CASE)

    // Price at the end of a row: "12.00", "RM 12.00", "1,234.50", "-5.00", "(5.00)", optional tax code "12.00 SR".
    private val trailingPrice = Regex("""(-|\()?\s*(?:RM\s*)?((?:\d{1,3}(?:,\d{3})+|\d+)[.,]\d{2})\)?\s*(?:[A-Z*]{1,2})?\s*$""", opts)
    private val leadingQty = Regex("""^(\d{1,2})\s*[xX@]?\s+(?=\D)""")
    private val inlineQty = Regex("""\s+(\d{1,2})\s*[xX@]\s*(?:RM\s*)?\d+[.,]\d{2}\s*$""", opts)
    private val trailingQty = Regex("""\s+[xX]\s*(\d{1,2})\s*$""")

    private val totalWords = Regex("""\b(grand total|total|jumlah|amount due|net total|nett|to pay)\b""", opts)
    // "Total Incl. 6% SST" is the grand total; "Total Excl. SST" is a subtotal. Both mention SST, so
    // they must be caught before the tax rule or the whole bill would be read as tax.
    private val inclTotalWords = Regex("""\b(incl(?:usive|\.)?|termasuk)\b""", opts)
    private val exclTotalWords = Regex("""\b(excl(?:usive|\.)?|sebelum|before)\b""", opts)
    private val countWords = Regex("""\btotal\s*(items?|qty|quantity)\b""", opts)
    // The tax summary table repeats the tax already counted above it. Everything after it is ignored.
    private val summaryStart = Regex("""\b(tax summary|ringkasan cukai|gst summary|sst summary)\b""", opts)
    // Payment lines name the bank or wallet used ("MAY BANK 58.90"); they are never items.
    private val paymentWords = Regex("""\b(may ?bank|mbb|cimb|rhb|public bank|bank islam|bimb|hong leong|ambank|tng|touch ?n ?go|boost|grab ?pay|shopee ?pay|qr pay|mae)\b""", opts)
    private val subtotalWords = Regex("""\b(sub[- ]?total|subtotal)\b""", opts)
    private val serviceWords = Regex("""\b(service|svc|s/?c|caj perkhidmatan|serv\.? ?chg)\b""", opts)
    private val taxWords = Regex("""\b(sst|gst|tax|cukai|vat)\b""", opts)
    private val roundingWords = Regex("""\b(round(?:ing)?|rounding adj|pelarasan)\b""", opts)
    private val discountWords = Regex("""\b(discount|diskaun|disc|promo|voucher|member price)\b""", opts)
    private val skipWords = Regex(
        """\b(cash|tunai|change|baki|card|visa|master(?:card)?|debit|credit|paid|payment|tender(?:ed)?|duitnow|ewallet|tel|phone|fax|invoice|receipt|resit|table|pax|cashier|server|date|time|reg\.? ?no|thank|terima kasih|order|bill no|no\. ?of items|items?\s*:|qty)\b""",
        opts,
    )

    /** Rebuilds visual rows from OCR lines by grouping lines whose vertical centres line up. */
    fun groupRows(lines: List<OcrLine>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val sorted = lines.sortedBy { it.centerY }
        val rows = ArrayList<MutableList<OcrLine>>()
        for (line in sorted) {
            val row = rows.lastOrNull()
            if (row != null) {
                val anchor = row.first()
                val tolerance = minOf(anchor.height, line.height) / 2
                if (kotlin.math.abs(line.centerY - anchor.centerY) <= tolerance) {
                    row += line
                    continue
                }
            }
            rows += mutableListOf(line)
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    fun parse(rows: List<String>): ParsedReceipt {
        val items = ArrayList<ReceiptItem>()
        var service = 0L
        var tax = 0L
        var rounding = 0L
        var discount = 0L
        var total: Long? = null
        var pendingName: String? = null

        for (raw in rows) {
            val row = raw.trim()
            if (row.isEmpty()) continue
            if (summaryStart.containsMatchIn(row)) break
            val priceMatch = trailingPrice.find(row)

            if (priceMatch == null) {
                // A name whose price was pushed onto the next row by OCR.
                pendingName = if (row.count { it.isLetter() } >= 2 && !skipWords.containsMatchIn(row)) row else null
                continue
            }

            val negative = priceMatch.groupValues[1].isNotEmpty()
            val amount = Money.parse(priceMatch.groupValues[2].replace(',', '.').let(::normaliseDecimal)) ?: continue
            var label = row.substring(0, priceMatch.range.first).trim()
            if (label.count { it.isLetter() } < 2 && pendingName != null) label = "$pendingName $label".trim()
            pendingName = null

            when {
                countWords.containsMatchIn(label) -> Unit
                totalWords.containsMatchIn(label) && exclTotalWords.containsMatchIn(label) -> Unit
                totalWords.containsMatchIn(label) && inclTotalWords.containsMatchIn(label) -> total = amount
                subtotalWords.containsMatchIn(label) -> Unit
                paymentWords.containsMatchIn(label) -> Unit
                roundingWords.containsMatchIn(label) -> rounding += if (negative) -amount else amount
                serviceWords.containsMatchIn(label) -> service += amount
                taxWords.containsMatchIn(label) -> tax += amount
                discountWords.containsMatchIn(label) || negative -> discount += amount
                totalWords.containsMatchIn(label) -> total = amount
                skipWords.containsMatchIn(label) -> Unit
                label.count { it.isLetter() } >= 2 -> items += toItem(label, amount)
            }
        }
        // No hint from plain OCR: let the printed total decide whether charges are already in the prices.
        return ChargeReconciler.reconcile(ParsedReceipt(items, service, tax, rounding, discount, total))
    }

    /** "1.234,50" style is rare in Malaysia; this only fixes "12,50" (comma as decimal point). */
    private fun normaliseDecimal(value: String): String {
        val lastDot = value.lastIndexOf('.')
        if (lastDot == -1) return value
        // Keep only the last dot as the decimal point, drop any earlier ones used as thousands separators.
        return value.substring(0, lastDot).replace(".", "") + value.substring(lastDot)
    }

    private fun toItem(label: String, amount: Long): ReceiptItem {
        var name = label
        var qty = 1
        inlineQty.find(name)?.let { m ->
            qty = m.groupValues[1].toInt()
            name = name.substring(0, m.range.first)
        }
        leadingQty.find(name)?.let { m ->
            qty = m.groupValues[1].toInt()
            name = name.substring(m.range.last + 1)
        }
        trailingQty.find(name)?.let { m ->
            qty = m.groupValues[1].toInt()
            name = name.substring(0, m.range.first)
        }
        name = name.trim().trim('-', '.', ':', '*').trim()
        return ReceiptItem(prettify(name), qty.coerceAtLeast(1), amount)
    }

    /** Receipts shout in capitals. "NASI LEMAK AYAM" reads better as "Nasi Lemak Ayam". */
    private fun prettify(name: String): String {
        if (name.any { it.isLowerCase() }) return name
        return name.lowercase().split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
    }
}
