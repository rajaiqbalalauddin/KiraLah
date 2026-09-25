package com.buyless.app.split

import org.junit.Assert.assertEquals
import org.junit.Test

// Receipt parsing and bill-splitting tests. The receipt rows are a made-up mamak bill covering the
// common layouts; add real receipts here whenever a scan comes out wrong.
class SplitTest {

    @Test fun parsesItemsAndCharges() {
        val r = ReceiptParser.parse(
            listOf(
                "RESTORAN MAMAK SELERA", "Tel: 03-1234 5678", "Table 5  Pax 4",
                "2 NASI LEMAK AYAM 24.00", "ROTI CANAI x3 4.50", "TEH TARIK 2 x 2.80 5.60",
                "MILO AIS", "3.20", "MEE GORENG MAMAK 9.50 SR", "Discount -2.00",
                "SUBTOTAL 44.80", "Service Charge 10% 4.48", "SST 6% 2.96", "Rounding Adj -0.04",
                "TOTAL RM 52.20", "CASH 60.00", "CHANGE 7.80",
            ),
        )
        assertEquals(listOf("Nasi Lemak Ayam", "Roti Canai", "Teh Tarik", "Milo Ais", "Mee Goreng Mamak"), r.items.map { it.name })
        assertEquals(listOf(2, 3, 2, 1, 1), r.items.map { it.qty })
        assertEquals(448L, r.serviceSen)
        assertEquals(296L, r.taxSen)
        assertEquals(-4L, r.roundingSen)
        assertEquals(200L, r.discountSen)
        assertEquals(5220L, r.totalSen)
        assertEquals(r.totalSen, r.itemsSen + r.extrasSen)
    }

    @Test fun rebuildsColumnLayout() {
        val rows = ReceiptParser.groupRows(
            listOf(
                OcrLine("NASI LEMAK", 10, 100, 200, 120), OcrLine("12.00", 400, 102, 460, 121),
                OcrLine("TEH O", 10, 130, 100, 150), OcrLine("2.50", 400, 131, 460, 149),
            ),
        )
        assertEquals(listOf("NASI LEMAK 12.00", "TEH O 2.50"), rows)
    }

    @Test fun splitAddsUpExactly() {
        val shares = SplitMath.compute(
            people = listOf(1, 2, 3),
            items = listOf(SplitItem(1, 1000, listOf(1, 2, 3)), SplitItem(2, 550, listOf(2))),
            extrasSen = 157,
        )
        assertEquals(1707L, shares.values.sumOf { it.totalSen })
        assertEquals(334L, shares.getValue(1).itemsSen) // the odd sen goes to the first owner
    }

    @Test fun negativeExtrasSplitEvenlyWhenNobodyAte() {
        assertEquals(listOf(-2L, -1L, -1L), SplitMath.distribute(-4, listOf(0, 0, 0)))
    }
}
