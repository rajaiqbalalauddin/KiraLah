package com.buyless.app.util

import com.buyless.app.recap.DayTotal
import com.buyless.app.recap.RecapBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

// Month periods that start on a chosen day: edges, short months, the new year, and the Recap fold.
class MonthPeriodsTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private val sep = YearMonth.of(2026, 9)

    @Test fun dayOneIsTheCalendarMonth() {
        assertEquals(d(2026, 9, 1), MonthPeriods.startOf(sep, 1))
        assertEquals(d(2026, 9, 30), MonthPeriods.lastDay(sep, 1))
        assertEquals(sep, MonthPeriods.periodOf(d(2026, 9, 30), 1))
        assertEquals(30, MonthPeriods.length(sep, 1))
        assertEquals("September", MonthPeriods.label(sep, 1, d(2026, 9, 27)))
    }

    @Test fun payday25thRunsTo24thNextMonth() {
        assertEquals(d(2026, 9, 25), MonthPeriods.startOf(sep, 25))
        assertEquals(d(2026, 10, 24), MonthPeriods.lastDay(sep, 25))
        assertEquals(sep, MonthPeriods.periodOf(d(2026, 9, 25), 25))
        assertEquals(sep, MonthPeriods.periodOf(d(2026, 10, 24), 25))
        assertEquals(YearMonth.of(2026, 10), MonthPeriods.periodOf(d(2026, 10, 25), 25))
        assertEquals(YearMonth.of(2026, 8), MonthPeriods.periodOf(d(2026, 9, 24), 25))
        assertEquals("25 Sep – 24 Oct", MonthPeriods.rangeText(sep, 25, d(2026, 9, 27)))
    }

    @Test fun periodCrossingNewYearShowsBothYears() {
        val dec = YearMonth.of(2026, 12)
        assertEquals(d(2027, 1, 24), MonthPeriods.lastDay(dec, 25))
        assertEquals(dec, MonthPeriods.periodOf(d(2027, 1, 10), 25))
        assertEquals("25 Dec 2026 – 24 Jan 2027", MonthPeriods.rangeText(dec, 25, d(2026, 12, 30)))
    }

    @Test fun day31FallsBackToLastDayInShortMonths() {
        // 2027 is not a leap year: Jan period is 31 Jan to 27 Feb, Feb period starts 28 Feb.
        val jan = YearMonth.of(2027, 1)
        val feb = YearMonth.of(2027, 2)
        assertEquals(d(2027, 1, 31), MonthPeriods.startOf(jan, 31))
        assertEquals(d(2027, 2, 28), MonthPeriods.startOf(feb, 31))
        assertEquals(d(2027, 3, 30), MonthPeriods.lastDay(feb, 31))
        assertEquals(jan, MonthPeriods.periodOf(d(2027, 2, 27), 31))
        assertEquals(feb, MonthPeriods.periodOf(d(2027, 2, 28), 31))
        assertEquals(d(2027, 4, 30), MonthPeriods.startOf(YearMonth.of(2027, 4), 31))
    }

    @Test fun everyDayBelongsToExactlyOnePeriod() {
        for (startDay in listOf(1, 15, 25, 29, 30, 31)) {
            var date = d(2026, 1, 1)
            while (date.year < 2029) {
                val ym = MonthPeriods.periodOf(date, startDay)
                val inside = !date.isBefore(MonthPeriods.startOf(ym, startDay)) && date.isBefore(MonthPeriods.endExclusive(ym, startDay))
                assertEquals("day $startDay, $date", true, inside)
                date = date.plusDays(1)
            }
        }
    }

    @Test fun recapFoldMatchesPeriods() {
        val days = listOf(
            DayTotal(d(2026, 9, 24), 1_000, 1), // August period
            DayTotal(d(2026, 9, 25), 2_000, 2), // September period starts
            DayTotal(d(2026, 10, 3), 3_000, 1), // still September period
            DayTotal(d(2026, 10, 25), 500, 1), // October period
        )
        val totals = RecapBuilder.monthTotals(days, 25)
        assertEquals(listOf(YearMonth.of(2026, 10), sep, YearMonth.of(2026, 8)), totals.map { it.ym })
        assertEquals(5_000L, totals[1].outSen)
        assertEquals(3, totals[1].count)
        // Day 1 is plain calendar months.
        assertEquals(listOf(YearMonth.of(2026, 10), sep), RecapBuilder.monthTotals(days, 1).map { it.ym })
    }

    @Test fun yearIsTheTwelvePeriodsStartingInIt() {
        assertEquals(d(2026, 1, 25), MonthPeriods.yearStart(2026, 25))
        assertEquals(d(2027, 1, 25), MonthPeriods.yearStart(2027, 25))
    }

    @Test fun recapWeeksCountFromPeriodStart() {
        val zone = java.time.ZoneId.of("Asia/Kuala_Lumpur")
        fun tx(date: LocalDate, sen: Long) = com.buyless.app.recap.RecapTx(
            sen, com.buyless.app.data.model.Direction.OUT, "Kopi", "FOOD", "MAE",
            date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        )
        val recap = RecapBuilder.build(
            listOf(tx(d(2026, 9, 25), 100), tx(d(2026, 10, 1), 200), tx(d(2026, 10, 2), 400), tx(d(2026, 10, 24), 800)),
            "September 2026", isYear = false,
            periodStart = d(2026, 9, 25), periodEnd = d(2026, 10, 24),
            previousSpentSen = null, zone = zone, today = d(2026, 10, 30), monthStartDay = 25,
        )
        assertEquals(listOf("Week 1" to 300L, "Week 2" to 400L, "Week 3" to 0L, "Week 4" to 0L, "Week 5" to 800L), recap.buckets)
    }
}
