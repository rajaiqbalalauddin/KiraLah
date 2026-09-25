package com.buyless.app.share

import com.buyless.app.split.SplitMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayMessageTest {

    @Test fun malaysianNumbersInAnyStyle() {
        assertEquals("60123456789", PhoneNumbers.normalize("012-345 6789"))
        assertEquals("60123456789", PhoneNumbers.normalize("+60 12-345 6789"))
        assertEquals("60123456789", PhoneNumbers.normalize("60123456789"))
        assertEquals("60123456789", PhoneNumbers.normalize("0060123456789"))
        assertEquals("60123456789", PhoneNumbers.normalize("123456789"))
        assertEquals("601112345678", PhoneNumbers.normalize("011-1234 5678"))
    }

    @Test fun foreignNumbersKeepTheirCode() {
        assertEquals("6591234567", PhoneNumbers.normalize("+65 9123 4567"))
    }

    @Test fun junkIsRejected() {
        assertNull(PhoneNumbers.normalize(""))
        assertNull(PhoneNumbers.normalize("12345"))
        assertNull(PhoneNumbers.normalize("abc"))
    }

    @Test fun prettyPrint() {
        assertEquals("+60 12-345 6789", PhoneNumbers.pretty("60123456789"))
        assertEquals("+60 11-1234 5678", PhoneNumbers.pretty("601112345678"))
    }

    @Test fun personMessageAddsUp() {
        val text = PayMessage.forPerson(
            name = "Aina Sofea",
            merchant = "Kedai Makan",
            dateText = "7 Sep",
            totalSen = 1240,
            lines = listOf(PayMessage.Line("Nasi Goreng Cina", 800, 1), PayMessage.Line("Teh O Limau", 240, 2)),
            extrasSen = 200,
            qrLabel = "MAE",
        )
        assertTrue(text.startsWith("Hi Aina! Your share for Kedai Makan (7 Sep) is RM 12.40."))
        assertTrue(text.contains("- Nasi Goreng Cina: RM 8.00"))
        assertTrue(text.contains("- half of Teh O Limau: RM 2.40"))
        assertTrue(text.contains("- Tax, service and rounding: RM 2.00"))
        assertTrue(text.contains("(MAE)"))
    }

    @Test fun groupMessageShowsWhoPaid() {
        val text = PayMessage.forGroup(
            "Kedai Makan", "7 Sep",
            listOf(PayMessage.GroupRow("Aina", 1240, paid = true), PayMessage.GroupRow("Hafiz", 980, paid = false)),
            qrLabel = null,
        )
        assertTrue(text.contains("- Aina: RM 12.40 (paid)"))
        assertTrue(text.contains("- Hafiz: RM 9.80\n"))
        assertTrue(text.contains("Please transfer when you can."))
    }

    @Test fun searchMatchesWordStartsAndPhone() {
        assertTrue(FriendSearch.matches("Aina Sofea", null, "sof"))
        assertTrue(FriendSearch.matches("Aina Sofea", null, ""))
        assertFalse(FriendSearch.matches("Aina Sofea", null, "ofe"))
        assertTrue(FriendSearch.matches("Hafiz", "60123456789", "0123"))
        assertEquals("aina sofea", FriendSearch.key("  Aina   Sofea "))
    }

    @Test fun itemShareMatchesSplitMath() {
        // RM10.01 shared by three: the first in table order gets the extra sen, same as the totals.
        val people = listOf(0L, 5L, 9L)
        val shares = people.map { SplitMath.shareOf(1001, listOf(9L, 0L, 5L), people, it) }
        assertEquals(listOf(334L, 334L, 333L), shares)
        assertEquals(1001L, shares.sum())
        assertEquals(0L, SplitMath.shareOf(1001, listOf(9L), people, 5L))
    }
}
