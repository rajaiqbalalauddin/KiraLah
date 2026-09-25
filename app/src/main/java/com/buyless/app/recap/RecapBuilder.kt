package com.buyless.app.recap

import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** The few fields a recap needs from a transaction. Keeps the builder free of Room and Android. */
data class RecapTx(
    val amountSen: Long,
    val direction: Direction,
    val merchant: String,
    val category: Category,
    val sourceLabel: String,
    val timestamp: Long,
)

data class MerchantStat(val name: String, val visits: Int, val spentSen: Long)

data class Personality(val title: String, val line: String)

/** Everything the story slides show for one month or one year. */
data class RecapData(
    val periodLabel: String,
    val isYear: Boolean,
    val spentSen: Long,
    val inSen: Long,
    val payments: Int,
    val previousSpentSen: Long?,
    val categories: List<Pair<Category, Long>>,
    val topMerchants: List<MerchantStat>,
    val biggest: RecapTx?,
    /** Spend per weekday, Monday first. */
    val weekdays: List<Long>,
    val busiestDay: DayOfWeek?,
    val apps: List<Pair<String, Long>>,
    /** Weeks of the month, or months of the year, for the rhythm chart. */
    val buckets: List<Pair<String, Long>>,
    val dailyAverageSen: Long,
    val noSpendDays: Int,
    val personality: Personality,
) {
    /** Percentage change vs the previous period, or null when there is nothing to compare. */
    val changePercent: Int?
        get() = previousSpentSen?.takeIf { it > 0 }?.let { ((spentSen - it) * 100 / it).toInt() }
}

/**
 * Turns raw transactions into Wrapped-style facts. Pure Kotlin, so every number on the story slides is
 * unit tested. Transfers between your own apps never reach here (the query excludes them).
 */
object RecapBuilder {

    fun build(
        txs: List<RecapTx>,
        periodLabel: String,
        isYear: Boolean,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        previousSpentSen: Long?,
        zone: ZoneId,
        today: LocalDate = LocalDate.now(zone),
    ): RecapData {
        val out = txs.filter { it.direction == Direction.OUT }
        val spent = out.sumOf { it.amountSen }
        val income = txs.filter { it.direction == Direction.IN }.sumOf { it.amountSen }

        val categories = out.groupBy { it.category }
            .mapValues { (_, v) -> v.sumOf { it.amountSen } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        // Merchants are grouped case-insensitively; the most common spelling is shown.
        val topMerchants = out.filter { it.merchant.isNotBlank() && !it.merchant.equals(it.sourceLabel, true) }
            .groupBy { it.merchant.trim().lowercase(Locale.ENGLISH) }
            .map { (_, v) ->
                val name = v.groupingBy { it.merchant.trim() }.eachCount().maxByOrNull { it.value }!!.key
                MerchantStat(name, v.size, v.sumOf { it.amountSen })
            }
            .sortedWith(compareByDescending<MerchantStat> { it.visits }.thenByDescending { it.spentSen })
            .take(5)

        val dates = out.map { it to Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        val weekdays = MutableList(7) { 0L }
        dates.forEach { (t, d) -> weekdays[d.dayOfWeek.value - 1] += t.amountSen }
        val busiest = weekdays.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.let { DayOfWeek.of(it.index + 1) }

        val apps = out.groupBy { it.sourceLabel }.mapValues { (_, v) -> v.sumOf { it.amountSen } }
            .entries.sortedByDescending { it.value }.map { it.key to it.value }

        // Only count days that have happened, so a month in progress is not diluted by future days.
        val lastDay = minOf(periodEnd, today)
        val days = (lastDay.toEpochDay() - periodStart.toEpochDay() + 1).coerceAtLeast(1)
        val spendDays = dates.map { it.second }.filter { !it.isAfter(lastDay) }.toSet().size
        val buckets = if (isYear) {
            (1..12).map { m ->
                val label = java.time.Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                label to dates.filter { it.second.monthValue == m }.sumOf { it.first.amountSen }
            }
        } else {
            (1..5).map { w ->
                "Week $w" to dates.filter { (it.second.dayOfMonth - 1) / 7 + 1 == w }.sumOf { it.first.amountSen }
            }.filter { (label, sen) -> sen > 0 || label != "Week 5" }
        }

        return RecapData(
            periodLabel = periodLabel,
            isYear = isYear,
            spentSen = spent,
            inSen = income,
            payments = out.size,
            previousSpentSen = previousSpentSen,
            categories = categories,
            topMerchants = topMerchants,
            biggest = out.maxByOrNull { it.amountSen },
            weekdays = weekdays,
            busiestDay = busiest,
            apps = apps,
            buckets = buckets,
            dailyAverageSen = spent / days,
            noSpendDays = (days - spendDays).toInt().coerceAtLeast(0),
            personality = personality(spent, income, categories),
        )
    }

    /** A playful label from where the money went. Pure fun, based only on the top category's share. */
    fun personality(spent: Long, income: Long, categories: List<Pair<Category, Long>>): Personality {
        if (spent == 0L) return Personality("The Monk", "Not a single ringgit out. Legendary restraint.")
        if (income > spent * 2) return Personality("The Stacker", "Way more came in than went out. Your future self says thanks.")
        val (top, sen) = categories.first()
        val share = sen * 100 / spent
        return when {
            share < 30 -> Personality("The All-Rounder", "No single habit ran the show. Balanced, mostly.")
            top == Category.FOOD -> Personality("The Foodie", "$share% of your spending went to food. Priorities, clearly.")
            top == Category.TRANSPORT -> Personality("The Road Warrior", "$share% went to getting around. Tolls, fuel and rides add up.")
            top == Category.SHOPPING -> Personality("The Cart Collector", "$share% went to shopping. Add to cart, then add to cart again.")
            top == Category.BILLS -> Personality("The Responsible One", "$share% went to bills and subscriptions. Adulting, fully unlocked.")
            else -> Personality("The Wildcard", "$share% went to things that fit no box. Mysterious.")
        }
    }
}
