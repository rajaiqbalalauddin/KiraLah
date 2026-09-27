package com.buyless.app.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * "Months" that start on a day the user picks (Settings > Month starts on), for example payday on the
 * 25th. A period is named by the YearMonth it starts in: with start day 25, 2026-09 means
 * 25 Sep to 24 Oct. With start day 1 everything is a plain calendar month, exactly as before.
 *
 * Pure date maths with the start day passed in, so it is unit tested and shared by Home, Activity,
 * Recap and monthly limits, which therefore always agree on where a month begins.
 *
 * Days 29 to 31 are allowed. In a month too short for the day, the period starts on that month's
 * last day instead (start day 31 means 28 Feb, 30 Apr...), so no period is ever skipped.
 */
object MonthPeriods {

    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val monthName = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    /** Keeps a stored value usable even if it was somehow saved out of range. */
    fun clampDay(day: Int): Int = day.coerceIn(1, 31)

    /** First day of the period named [ym]. */
    fun startOf(ym: YearMonth, startDay: Int): LocalDate = ym.atDay(minOf(clampDay(startDay), ym.lengthOfMonth()))

    /** First day after the period, i.e. the next period's start. */
    fun endExclusive(ym: YearMonth, startDay: Int): LocalDate = startOf(ym.plusMonths(1), startDay)

    /** Last day inside the period. */
    fun lastDay(ym: YearMonth, startDay: Int): LocalDate = endExclusive(ym, startDay).minusDays(1)

    /** Which period a date falls in: this calendar month's period if it has started, else last month's. */
    fun periodOf(date: LocalDate, startDay: Int): YearMonth {
        val ym = YearMonth.from(date)
        return if (date.isBefore(startOf(ym, startDay))) ym.minusMonths(1) else ym
    }

    fun current(zone: ZoneId, startDay: Int): YearMonth = periodOf(LocalDate.now(zone), startDay)

    /** Epoch-millis range, start inclusive and end exclusive, for database queries. */
    fun range(ym: YearMonth, zone: ZoneId, startDay: Int): Pair<Long, Long> =
        startOf(ym, startDay).atStartOfDay(zone).toInstant().toEpochMilli() to
            endExclusive(ym, startDay).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Number of days in the period (28 to 31). */
    fun length(ym: YearMonth, startDay: Int): Int =
        ChronoUnit.DAYS.between(startOf(ym, startDay), endExclusive(ym, startDay)).toInt()

    /**
     * A year's worth of periods: the twelve that start in [year]. With start day 25 that is
     * 25 Jan to 24 Jan next year, so a year total always equals the sum of its month cards.
     */
    fun yearStart(year: Int, startDay: Int): LocalDate = startOf(YearMonth.of(year, 1), startDay)

    /** "25 Sep – 24 Oct". Years are added only when needed: the range is not this year, or it crosses one. */
    fun rangeText(ym: YearMonth, startDay: Int, today: LocalDate): String {
        val a = startOf(ym, startDay)
        val b = lastDay(ym, startDay)
        return if (a.year == b.year && a.year == today.year) "${a.format(dayMonth)} – ${b.format(dayMonth)}"
        else if (a.year == b.year) "${a.format(dayMonth)} – ${b.format(dayMonthYear)}"
        else "${a.format(dayMonthYear)} – ${b.format(dayMonthYear)}"
    }

    /**
     * Short label for the month switcher: "September" for calendar months, "25 Sep – 24 Oct" otherwise.
     * No years in the range form, because the switcher shares the header row with the logo and must stay
     * narrow; the Home headline (spentTitle) carries the full range with years when they matter.
     */
    fun label(ym: YearMonth, startDay: Int, today: LocalDate): String =
        if (clampDay(startDay) == 1) (if (ym.year == today.year) ym.format(monthName) else ym.format(monthYear))
        else "${startOf(ym, startDay).format(dayMonth)} – ${lastDay(ym, startDay).format(dayMonth)}"

    /** "Spent in September" or "Spent 25 Sep – 24 Oct", for the Home headline. */
    fun spentTitle(ym: YearMonth, startDay: Int, today: LocalDate): String =
        if (clampDay(startDay) == 1) "Spent in ${label(ym, startDay, today)}" else "Spent ${rangeText(ym, startDay, today)}"

    /** How many periods lie between two (b minus a), for chart and recap buckets. */
    fun monthsBetween(a: YearMonth, b: YearMonth): Int = ChronoUnit.MONTHS.between(a, b).toInt()
}
