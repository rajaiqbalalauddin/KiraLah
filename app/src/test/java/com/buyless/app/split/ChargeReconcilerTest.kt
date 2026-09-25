package com.buyless.app.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real receipts that broke the old "always add tax on top" rule, plus the normal restaurant case. */
class ChargeReconcilerTest {

    // Kedai makan in Bandar Puteri Puchong, 7 Sep 2026. Items add up to RM58.90 and the receipt prints
    // "Total Incl. 6% SST 58.90", so the RM3.32 SST is already inside the prices.
    private val puchongItems = listOf(230L, 950, 150, 230, 480, 580, 50, 750, 570, 1100, 800)
        .mapIndexed { i, p -> ReceiptItem("Item $i", 1, p) }

    @Test fun taxAlreadyInPricesIsNotAddedAgain() {
        val r = ChargeReconciler.reconcile(ParsedReceipt(puchongItems, taxSen = 332, totalSen = 5890))
        assertTrue(r.taxIncluded)
        assertEquals(5890L, r.sumSen)
        assertEquals(0L, r.mismatchSen)
    }

    @Test fun numbersBeatAWrongHint() {
        val r = ChargeReconciler.reconcile(ParsedReceipt(puchongItems, taxSen = 332, totalSen = 5890), hintTaxIncluded = false)
        assertTrue(r.taxIncluded)
    }

    @Test fun normalRestaurantAddsServiceAndTaxOnTop() {
        // 100.00 food + 10% service + 6% SST = 116.00
        val r = ChargeReconciler.reconcile(
            ParsedReceipt(listOf(ReceiptItem("Food", 1, 10000)), serviceSen = 1000, taxSen = 600, totalSen = 11600),
        )
        assertFalse(r.taxIncluded)
        assertFalse(r.serviceIncluded)
        assertEquals(11600L, r.sumSen)
    }

    @Test fun serviceOnTopButTaxInside() {
        val r = ChargeReconciler.reconcile(
            ParsedReceipt(listOf(ReceiptItem("Food", 1, 10000)), serviceSen = 1000, taxSen = 566, totalSen = 11000),
        )
        assertTrue(r.taxIncluded)
        assertFalse(r.serviceIncluded)
    }

    @Test fun cashRoundingStillMatches() {
        // 58.88 inclusive, rounded to 58.90 on the receipt without a rounding line.
        val items = listOf(ReceiptItem("A", 1, 5888))
        val r = ChargeReconciler.reconcile(ParsedReceipt(items, taxSen = 333, totalSen = 5890))
        assertTrue(r.taxIncluded)
    }

    @Test fun nothingAddsUpFallsBackToHint() {
        val items = listOf(ReceiptItem("A", 1, 4000)) // An item was missed; neither reading matches.
        assertTrue(ChargeReconciler.reconcile(ParsedReceipt(items, taxSen = 332, totalSen = 5890), hintTaxIncluded = true).taxIncluded)
        assertFalse(ChargeReconciler.reconcile(ParsedReceipt(items, taxSen = 332, totalSen = 5890)).taxIncluded)
    }

    @Test fun noPrintedTotalUsesHint() {
        val items = listOf(ReceiptItem("A", 1, 1000))
        assertTrue(ChargeReconciler.reconcile(ParsedReceipt(items, taxSen = 57), hintTaxIncluded = true).taxIncluded)
        assertFalse(ChargeReconciler.reconcile(ParsedReceipt(items, taxSen = 57)).taxIncluded)
    }

    @Test fun ocrReadsThePuchongSummaryCorrectly() {
        val rows = listOf(
            "AYAM 1 2.30 2.30", "SAYUR 1 9.50 9.50", "TELUR DADAR 1 1.50 1.50", "BARLI (SUAM) 1 2.30 2.30",
            "TEH O LIMAU AIS BUNGKUS 2 2.40 4.80", "AIS KOSONG 2 2.90 5.80", "NASI GORENG CILIPADI 1 0.50 0.50",
            "AYAM GORENG 1 7.50 7.50", "NASI GORENG AYAM (Dada) 1 5.70 5.70", "NASI GORENG CINA 1 11.00 11.00",
            "EXTRA 1 8.00 8.00",
            "Total Items = 11.00", "Total Qty = 13.00",
            "Sub Total RM 58.90", "Voucher - RM 0.00", "Total Excl.6% SST RM 55.58", "SST 6% RM 3.32",
            "Total Incl.6% SST RM 58.90", "Rounding RM 0.00", "MAY BANK RM 58.90", "CHANGE RM 0.00",
            "TAX SUMMARY AMT (RM) TAX (RM)", "Service Tax 6 % 55.58 3.32",
        )
        val r = ReceiptParser.parse(rows)
        assertEquals(11, r.items.size)
        assertEquals(5890L, r.totalSen)
        assertEquals(332L, r.taxSen)
        assertTrue(r.taxIncluded)
        assertEquals(5890L, r.sumSen)
    }
}
