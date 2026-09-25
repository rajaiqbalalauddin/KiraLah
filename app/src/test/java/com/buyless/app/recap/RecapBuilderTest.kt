package com.buyless.app.recap

import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// Recap facts for a small made-up September. Checks the numbers the story slides show.
class RecapBuilderTest {

    private val zone = ZoneId.of("Asia/Kuala_Lumpur")

    private fun tx(sen: Long, merchant: String, cat: Category, day: Int, app: String = "MAE", dir: Direction = Direction.OUT) =
        RecapTx(sen, dir, merchant, cat, app, LocalDateTime.of(2026, 9, day, 12, 0).atZone(zone).toInstant().toEpochMilli())

    private val txs = listOf(
        tx(1250, "Kedai Kopi Ali", Category.FOOD, 1), // Tuesday
        tx(1300, "kedai kopi ali", Category.FOOD, 8), // Tuesday
        tx(1100, "Kedai Kopi Ali", Category.FOOD, 15), // Tuesday
        tx(420, "Toll", Category.TRANSPORT, 4, app = "TNG"), // Friday
        tx(20650, "Jibby Chow", Category.FOOD, 20, app = "BIMB"), // Sunday
        tx(240000, "Salary", Category.INCOME, 25, app = "BIMB", dir = Direction.IN),
    )

    private val recap = RecapBuilder.build(
        txs, "September 2026", isYear = false,
        periodStart = LocalDate.of(2026, 9, 1), periodEnd = LocalDate.of(2026, 9, 30),
        previousSpentSen = 30000, zone = zone, today = LocalDate.of(2026, 10, 5),
    )

    @Test fun totals() {
        assertEquals(24720L, recap.spentSen)
        assertEquals(240000L, recap.inSen)
        assertEquals(5, recap.payments)
        assertEquals(-17, recap.changePercent)
        assertEquals(824L, recap.dailyAverageSen) // 24720 / 30 days
    }

    @Test fun topMerchantGroupsSpellings() {
        val top = recap.topMerchants.first()
        assertEquals("Kedai Kopi Ali", top.name)
        assertEquals(3, top.visits)
        assertEquals(3650L, top.spentSen)
    }

    @Test fun categoriesAndBiggest() {
        assertEquals(Category.FOOD, recap.categories.first().first)
        assertEquals(20650L, recap.biggest?.amountSen)
        assertEquals(DayOfWeek.SUNDAY, recap.busiestDay)
        assertEquals("BIMB", recap.apps.first().first)
    }

    @Test fun personality() {
        assertTrue(recap.personality.title == "The Stacker")
        assertEquals("The Monk", RecapBuilder.personality(0, 0, emptyList()).title)
        assertEquals("The Foodie", RecapBuilder.personality(1000, 0, listOf(Category.FOOD to 800L, Category.OTHER to 200L)).title)
    }
}
