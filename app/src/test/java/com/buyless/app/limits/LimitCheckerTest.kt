package com.buyless.app.limits

import com.buyless.app.data.model.ALL_CATEGORIES
import com.buyless.app.data.model.LimitPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

// Spending limit rules: windows, the 80% heads-up, and "reached" on the crossing payment and every one after.
class LimitCheckerTest {

    private val zone = ZoneId.of("Asia/Kuala_Lumpur")

    /** 2026-09-<day> at hh:mm KL time. 1 Sep 2026 is a Tuesday. */
    private fun at(day: Int, hour: Int = 12, month: Int = 9) =
        LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun row(sen: Long, cat: String, t: Long) = SpendRow(cat, sen, t)

    private val monthlyFood = Limit(1, LimitPeriod.MONTH, "FOOD", 10_000) // RM 100
    private val dailyAll = Limit(2, LimitPeriod.DAY, ALL_CATEGORIES, 5_000) // RM 50

    /** Runs the checker the way LimitAlerts does: the new payment is already in the rows. */
    private fun check(limits: List<Limit>, earlier: List<SpendRow>, tx: SpendRow, now: Long = tx.timestamp) =
        LimitChecker.check(limits, earlier + tx, tx, now, zone)

    @Test fun weekStartsMondayAndCanBeginLastMonth() {
        // Thu 1 Oct 2026 belongs to the week starting Mon 28 Sep.
        val w = LimitPeriods.window(LimitPeriod.WEEK, at(1, month = 10), zone)
        assertEquals(at(28, hour = 0), w.from)
        assertEquals(LimitPeriods.window(LimitPeriod.WEEK, at(1, month = 10), zone).from, LimitPeriods.earliestStart(at(1, month = 10), zone))
    }

    @Test fun underWarningIsSilent() {
        val hits = check(listOf(monthlyFood), listOf(row(3_000, "FOOD", at(2))), row(2_000, "FOOD", at(3)))
        assertTrue(hits.isEmpty())
    }

    @Test fun crossingEightyPercentWarnsOnce() {
        val earlier = listOf(row(7_000, "FOOD", at(2)))
        val first = check(listOf(monthlyFood), earlier, row(1_000, "FOOD", at(3))) // 70% -> 80%
        assertEquals(listOf(LimitAlert.WARNING), first.map { it.alert })
        assertEquals(80, first.single().status.percent)

        val second = check(listOf(monthlyFood), earlier + row(1_000, "FOOD", at(3)), row(500, "FOOD", at(4))) // 80% -> 85%
        assertTrue(second.isEmpty())
    }

    @Test fun crossingPaymentAndEveryLaterOneAreReached() {
        val earlier = listOf(row(9_000, "FOOD", at(2)))
        val crossing = row(1_500, "FOOD", at(3))
        assertEquals(listOf(LimitAlert.REACHED), check(listOf(monthlyFood), earlier, crossing).map { it.alert })

        val later = check(listOf(monthlyFood), earlier + crossing, row(300, "FOOD", at(4)))
        assertEquals(LimitAlert.REACHED, later.single().alert)
        assertEquals(10_800L, later.single().status.spentSen)
        assertEquals(-800L, later.single().status.leftSen)
    }

    @Test fun jumpStraightPastLimitIsReachedNotWarning() {
        val hits = check(listOf(monthlyFood), emptyList(), row(12_000, "FOOD", at(3)))
        assertEquals(listOf(LimitAlert.REACHED), hits.map { it.alert })
    }

    @Test fun otherCategoriesDoNotCountOrTrigger() {
        val earlier = listOf(row(9_900, "FOOD", at(2)))
        assertTrue(check(listOf(monthlyFood), earlier, row(5_000, "SHOPPING", at(3))).isEmpty())
    }

    @Test fun allCategoriesLimitCountsEverythingThatDay() {
        val earlier = listOf(row(2_000, "FOOD", at(3, hour = 9)), row(2_000, "custom:4", at(3, hour = 10)), row(9_000, "BILLS", at(2)))
        val hits = check(listOf(dailyAll), earlier, row(1_000, "TRANSPORT", at(3, hour = 18)))
        assertEquals(LimitAlert.REACHED, hits.single().alert) // 20 + 20 + 10 = RM 50; yesterday's bill ignored
        assertEquals(5_000L, hits.single().status.spentSen)
    }

    @Test fun latePaymentFromEndedDayIsSkippedForDailyLimit() {
        val yesterday = row(6_000, "FOOD", at(2, hour = 20))
        val hits = check(listOf(dailyAll, monthlyFood), emptyList(), yesterday, now = at(3, hour = 9))
        assertTrue(hits.none { it.status.limit.period == LimitPeriod.DAY })
    }

    @Test fun reachedComesFirstThenLongerWindows() {
        val earlier = listOf(row(8_000, "FOOD", at(2)), row(4_500, "FOOD", at(3, hour = 8)))
        // Food this month: 125 + 5 -> reached. All today: 45 + 5 = 50 -> reached too.
        val hits = check(listOf(dailyAll, monthlyFood), earlier, row(500, "FOOD", at(3, hour = 13)))
        assertEquals(listOf(LimitPeriod.MONTH, LimitPeriod.DAY), hits.map { it.status.limit.period })
    }
}
