package com.buyless.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

// A saved split must reopen exactly as it was left: people, owners, charges and who has paid.
class SplitHistoryCodecTest {

    @Test fun roundTrip() {
        val original = SplitSnapshot(
            merchant = "Restoran Selera",
            people = listOf(SplitSnapshot.SnapPerson(0, "Me", 0), SplitSnapshot.SnapPerson(5, "Aina", 1, friendId = 12, phone = "60123456789")),
            items = listOf(
                SplitSnapshot.SnapItem(1, "Nasi Lemak", 1200, listOf(0, 5), "Extra sambal"),
                SplitSnapshot.SnapItem(2, "Teh O", 250, listOf(5), null),
            ),
            serviceText = "1.45",
            taxText = "0.87",
            roundingText = "-0.02",
            discountText = "",
            receiptTotalSen = 1680,
            paid = setOf(5),
            sent = setOf(5),
        )
        assertEquals(original, SplitHistoryRepository.decode(SplitHistoryRepository.encode(original)))
    }

    @Test fun missingOptionalFields() {
        val s = SplitHistoryRepository.decode("""{"people":[],"items":[],"merchant":null,"receiptTotal":null}""")
        assertEquals(null, s.merchant)
        assertEquals(null, s.receiptTotalSen)
        assertEquals(emptySet<Long>(), s.paid)
    }
}
