package com.buyless.app.parser

import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import com.buyless.app.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Parser tests. The sample texts are made up to cover the patterns we expect. Replace them with real
// MAE / BIMB / TNG notifications once you have logged a few, and keep adding one test per new format.
class NotificationParserTest {

    private fun parsed(text: String) = NotificationParser.parse(text) as NotificationParser.Result.Parsed

    @Test fun paymentOut() {
        val r = parsed("You've paid RM12.50 to Kedai Kopi Ali on 24 Sep.")
        assertEquals(1250L, r.amountSen)
        assertEquals(Direction.OUT, r.direction)
        assertEquals("Kedai Kopi Ali", r.merchant)
        assertEquals(Category.FOOD, r.category)
    }

    @Test fun transferReceivedIsIn() {
        val r = parsed("Transfer received. RM2,400.00 from Syarikat ABC Sdn Bhd.")
        assertEquals(240000L, r.amountSen)
        assertEquals(Direction.IN, r.direction)
        assertEquals(Category.INCOME, r.category)
    }

    @Test fun malayWording() {
        val r = parsed("Bayaran RM 38.90 kepada MYDIN berjaya")
        assertEquals(3890L, r.amountSen)
        assertEquals(Direction.OUT, r.direction)
    }

    @Test fun otpIsNeverStored() {
        assertTrue(NotificationParser.parse("Your TAC is 123456 for RM500 transfer") is NotificationParser.Result.Ignore)
    }

    @Test fun promoIsIgnored() {
        assertTrue(NotificationParser.parse("Get RM5 cashback vouchers this weekend only!") is NotificationParser.Result.Ignore)
    }

    @Test fun nonMoneyIsIgnored() {
        assertTrue(NotificationParser.parse("New features are here. Update now.") is NotificationParser.Result.Ignore)
    }

    @Test fun missingAmountStillFlagged() {
        val r = parsed("Payment successful")
        assertNull(r.amountSen)
        assertEquals(Direction.OUT, r.direction)
    }

    @Test fun moneyFormatting() {
        assertEquals("RM 1,284.60", Money.format(128460))
        assertEquals("RM 0.05", Money.format(5))
        assertEquals(1000000L, Money.parse("10,000"))
        assertNull(Money.parse("1.234"))
    }
}
