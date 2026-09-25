package com.buyless.app.util

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ringgit formatting and parsing on whole sen. Done by hand (no NumberFormat) because it runs for
 * every list row and this version allocates almost nothing and never depends on device locale.
 */
object Money {

    fun format(sen: Long): String {
        val abs = kotlin.math.abs(sen)
        val ringgit = abs / 100
        val cents = (abs % 100).toInt()
        val digits = ringgit.toString()
        val sb = StringBuilder(digits.length + 8).append("RM ")
        for (i in digits.indices) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
            sb.append(digits[i])
        }
        sb.append('.')
        if (cents < 10) sb.append('0')
        sb.append(cents)
        return sb.toString()
    }

    /** "+RM 5.00" / "-RM 5.00". Sign is explicit so direction does not rely on colour alone. */
    fun formatSigned(sen: Long, incoming: Boolean): String = (if (incoming) "+" else "-") + format(sen)

    /** User input or regex capture to sen. Returns null for empty, zero, negative or over-precise values. */
    fun parse(input: String): Long? {
        val clean = input.replace(",", "").replace("RM", "", ignoreCase = true).trim()
        if (clean.isEmpty()) return null
        return try {
            val value = BigDecimal(clean)
            if (value.signum() <= 0 || value.scale() > 2) null else value.movePointRight(2).longValueExact()
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Sen back to an editable string, for pre-filling the amount field. */
    fun toInput(sen: Long): String = "${sen / 100}.${(sen % 100).toString().padStart(2, '0')}"
}

/**
 * Date helpers. Formatters are built once and reused: DateTimeFormatter is immutable and thread-safe,
 * and building one per row is a measurable cost in long lists.
 */
object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayFull = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val monthName = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val fullDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** Start (inclusive) and end (exclusive) of a month in epoch millis, for range queries. */
    fun monthRange(month: YearMonth): Pair<Long, Long> {
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to to
    }

    fun toDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun timeOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(time)

    /** "Today", "Yesterday" or "22 Sep", which reads faster than a full date. */
    fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now(zone)): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) date.format(dayMonth) else date.format(fullDate)
    }

    fun sectionLabel(date: LocalDate, today: LocalDate = LocalDate.now(zone)): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayFull)
    }

    fun monthLabel(month: YearMonth): String =
        if (month.year == YearMonth.now(zone).year) month.format(monthName) else month.format(monthYear)

    fun fullDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(fullDate)
}
