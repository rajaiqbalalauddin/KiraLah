package com.buyless.app.limits

import com.buyless.app.data.model.ALL_CATEGORIES
import com.buyless.app.data.model.LimitPeriod
import com.buyless.app.util.MonthPeriods
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// Pure spending-limit maths: no Android, no Room. Kept apart so the rules that decide when you get
// a notification can be unit tested on a PC, and so the listener and the Limits screen share them.

/** One counted payment out, as the limit maths needs it. Also a Room result row (column names match). */
data class SpendRow(val category: String, val amountSen: Long, val timestamp: Long)

/** A limit as the maths sees it, free of Room. */
data class Limit(val id: Long, val period: LimitPeriod, val categoryKey: String, val amountSen: Long)

/** A time window: from inclusive, to exclusive, in epoch millis. */
data class Window(val from: Long, val to: Long) {
    operator fun contains(t: Long) = t >= from && t < to
}

/** Where one limit stands in its current window. */
data class LimitStatus(val limit: Limit, val spentSen: Long, val window: Window) {
    val leftSen: Long get() = limit.amountSen - spentSen
    val reached: Boolean get() = spentSen >= limit.amountSen

    /** 0.0 to 1.0+ for the progress bar. A zero limit counts as full straight away. */
    val fraction: Float get() = if (limit.amountSen <= 0) 1f else spentSen.toFloat() / limit.amountSen

    /** Whole percent used, rounded down, so "80%" only shows once 80% really is used. */
    val percent: Int get() = if (limit.amountSen <= 0) 100 else (spentSen * 100 / limit.amountSen).toInt()
}

/** Why a notification is sent for a limit. */
enum class LimitAlert { WARNING, REACHED }

data class LimitHit(val status: LimitStatus, val alert: LimitAlert)

object LimitPeriods {
    /** Share of a limit at which the one-time "getting close" warning fires. */
    const val WARNING_PERCENT = 80

    /**
     * The calendar window of [period] that contains [at]: that day, that Monday-to-Sunday week, or
     * that month. Calendar windows (not "the last 7 days") because that is how people budget.
     * Months begin on [monthStartDay] (Settings > Month starts on), the same periods Home and Recap use.
     */
    fun window(period: LimitPeriod, at: Long, zone: ZoneId, monthStartDay: Int = 1): Window {
        val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val start: LocalDate
        val end: LocalDate
        when (period) {
            LimitPeriod.DAY -> { start = date; end = date.plusDays(1) }
            LimitPeriod.WEEK -> { start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); end = start.plusWeeks(1) }
            LimitPeriod.MONTH -> {
                val ym = MonthPeriods.periodOf(date, monthStartDay)
                start = MonthPeriods.startOf(ym, monthStartDay)
                end = MonthPeriods.endExclusive(ym, monthStartDay)
            }
        }
        return Window(start.atStartOfDay(zone).toInstant().toEpochMilli(), end.atStartOfDay(zone).toInstant().toEpochMilli())
    }

    /**
     * Earliest moment any current window starts. A week can begin in the previous month, so this is
     * the earlier of this week's Monday and the month's start. Loading rows from here covers all limits.
     */
    fun earliestStart(now: Long, zone: ZoneId, monthStartDay: Int = 1): Long =
        minOf(window(LimitPeriod.WEEK, now, zone).from, window(LimitPeriod.MONTH, now, zone, monthStartDay).from)

    fun matches(limit: Limit, category: String): Boolean =
        limit.categoryKey == ALL_CATEGORIES || limit.categoryKey == category
}

object LimitChecker {

    /** Current standing of every limit, for the Limits screen. */
    fun statuses(limits: List<Limit>, rows: List<SpendRow>, now: Long, zone: ZoneId, monthStartDay: Int = 1): List<LimitStatus> =
        limits.map { limit ->
            val window = LimitPeriods.window(limit.period, now, zone, monthStartDay)
            LimitStatus(limit, spent(limit, rows, window), window)
        }

    /**
     * Which limits a just-saved payment should notify about. [rows] must already include [tx].
     *
     * - REACHED when the window total is at or over the limit. This covers the payment that crosses
     *   the line and every payment after it in the same window, which is the reminder you asked for.
     * - WARNING when this payment is the one that moves the total from under 80% to 80% or more,
     *   while still under the limit. Worked out from before/after totals, so it fires once per window
     *   without storing any "already warned" flag.
     *
     * A payment from an earlier window (a late alert about yesterday) is skipped for that period,
     * because "your daily limit is reached" about a day that has ended would only confuse.
     */
    fun check(limits: List<Limit>, rows: List<SpendRow>, tx: SpendRow, now: Long, zone: ZoneId, monthStartDay: Int = 1): List<LimitHit> {
        val hits = ArrayList<LimitHit>()
        for (limit in limits) {
            if (!LimitPeriods.matches(limit, tx.category)) continue
            val window = LimitPeriods.window(limit.period, now, zone, monthStartDay)
            if (tx.timestamp !in window) continue
            val after = spent(limit, rows, window)
            val before = after - tx.amountSen
            val status = LimitStatus(limit, after, window)
            when {
                after >= limit.amountSen -> hits += LimitHit(status, LimitAlert.REACHED)
                atWarning(after, limit.amountSen) && !atWarning(before, limit.amountSen) -> hits += LimitHit(status, LimitAlert.WARNING)
            }
        }
        // Reached first, then the longer windows first: "Monthly all" matters more than "Daily food".
        return hits.sortedWith(compareBy({ it.alert != LimitAlert.REACHED }, { -it.status.limit.period.ordinal }))
    }

    private fun spent(limit: Limit, rows: List<SpendRow>, window: Window): Long =
        rows.sumOf { if (it.timestamp in window && LimitPeriods.matches(limit, it.category)) it.amountSen else 0L }

    /** Integer maths so 80% of RM 0.99 never rounds the wrong way. */
    private fun atWarning(spent: Long, limit: Long): Boolean = spent * 100 >= limit * LimitPeriods.WARNING_PERCENT
}
