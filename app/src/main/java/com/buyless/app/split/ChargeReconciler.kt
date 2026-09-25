package com.buyless.app.split

/**
 * Works out whether a receipt's service charge and SST are added on top of the item prices, or are
 * already inside them (common in Malaysian kopitiams and mamaks: "Total Incl. 6% SST").
 *
 * Why numbers first: the printed total is the one figure that cannot be misread as a label. So we try
 * every way the charges could combine with the items and keep the one that lands on that total. The
 * reader's own "prices include tax" hint only decides when the numbers cannot (no total printed, or
 * nothing adds up because an item was misread).
 */
object ChargeReconciler {

    /** Within 5 sen counts as a match. Covers 5-sen cash rounding and a stray sen from the reader. */
    const val TOLERANCE_SEN = 5L

    /** One way to read the charges. Listed in order of how common they are, most common first. */
    private data class Reading(val serviceIncluded: Boolean, val taxIncluded: Boolean)

    private val readings = listOf(
        Reading(serviceIncluded = false, taxIncluded = false), // Added on top: the usual restaurant bill.
        Reading(serviceIncluded = false, taxIncluded = true),  // Prices include SST, service added.
        Reading(serviceIncluded = true, taxIncluded = true),   // Everything already in the prices.
        Reading(serviceIncluded = true, taxIncluded = false),  // Rare, kept for completeness.
    )

    /**
     * Returns [receipt] with serviceIncluded / taxIncluded set.
     * [hintTaxIncluded] and [hintServiceIncluded] come from Gemini; null when the reader gave none.
     */
    fun reconcile(
        receipt: ParsedReceipt,
        hintTaxIncluded: Boolean? = null,
        hintServiceIncluded: Boolean? = null,
    ): ParsedReceipt {
        val hinted = Reading(
            serviceIncluded = hintServiceIncluded ?: false,
            taxIncluded = hintTaxIncluded ?: false,
        )
        // Nothing to decide when there are no charges.
        if (receipt.serviceSen == 0L && receipt.taxSen == 0L) return receipt.copy(serviceIncluded = false, taxIncluded = false)

        val printed = receipt.totalSen ?: return receipt.apply(hinted)

        // Only readings that actually change the sum are worth comparing. With no service charge,
        // "service included" and "service added" give the same number, so drop the duplicate.
        val candidates = readings.filter { r ->
            (receipt.serviceSen != 0L || !r.serviceIncluded) && (receipt.taxSen != 0L || !r.taxIncluded)
        }
        val matching = candidates.filter { kotlin.math.abs(receipt.apply(it).sumSen - printed) <= TOLERANCE_SEN }

        val chosen = when {
            matching.isEmpty() -> hinted                   // Nothing adds up: an item is wrong, trust the hint.
            hinted in matching -> hinted                   // Numbers and hint agree.
            else -> matching.minBy { kotlin.math.abs(receipt.apply(it).sumSen - printed) } // Closest; ties keep list order.
        }
        return receipt.apply(chosen)
    }

    private fun ParsedReceipt.apply(r: Reading) = copy(serviceIncluded = r.serviceIncluded, taxIncluded = r.taxIncluded)
}
