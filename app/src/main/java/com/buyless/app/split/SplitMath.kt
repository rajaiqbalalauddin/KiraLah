package com.buyless.app.split

/** An item on the split board. owners is who shares it; empty means nobody has claimed it yet. */
data class SplitItem(val id: Long, val priceSen: Long, val owners: List<Long>)

/** What one person owes: their items plus their fair part of tax, service, rounding and discounts. */
data class Share(val itemsSen: Long, val extrasSen: Long) {
    val totalSen: Long get() = itemsSen + extrasSen
}

/**
 * Splits a bill down to the last sen, so everyone's totals always add up to the receipt exactly.
 *
 * - A shared item is divided evenly; leftover sen go to the first owners in order.
 * - Charges are divided in proportion to what each person ate (the fair way for service and SST),
 *   using the largest-remainder method so rounding never loses or invents a sen.
 */
object SplitMath {

    fun compute(people: List<Long>, items: List<SplitItem>, extrasSen: Long): Map<Long, Share> {
        val itemTotals = LinkedHashMap<Long, Long>().apply { people.forEach { put(it, 0L) } }
        val order = people.withIndex().associate { it.value to it.index }

        for (item in items) {
            val owners = item.owners.filter { it in itemTotals }.sortedBy { order[it] }
            if (owners.isEmpty()) continue
            val base = item.priceSen / owners.size
            val remainder = (item.priceSen % owners.size).toInt()
            owners.forEachIndexed { i, id ->
                itemTotals[id] = itemTotals.getValue(id) + base + if (i < remainder) 1 else 0
            }
        }

        val extras = distribute(extrasSen, people.map { itemTotals.getValue(it) })
        return people.withIndex().associate { (i, id) -> id to Share(itemTotals.getValue(id), extras[i]) }
    }

    /**
     * One person's part of one item, using the same rule as [compute] (leftover sen go to the first
     * owners in [people] order), so the WhatsApp breakdown matches the Totals screen to the sen.
     */
    fun shareOf(priceSen: Long, owners: List<Long>, people: List<Long>, person: Long): Long {
        val order = people.withIndex().associate { it.value to it.index }
        val sorted = owners.filter { it in order }.sortedBy { order[it] }
        val index = sorted.indexOf(person)
        if (index < 0) return 0L
        val remainder = (priceSen % sorted.size).toInt()
        return priceSen / sorted.size + if (index < remainder) 1 else 0
    }

    /** Splits [amount] by [weights]. Falls back to equal parts when nobody has any weight yet. */
    fun distribute(amount: Long, weights: List<Long>): List<Long> {
        if (weights.isEmpty()) return emptyList()
        if (amount == 0L) return weights.map { 0L }
        val sign = if (amount < 0) -1 else 1
        val abs = kotlin.math.abs(amount)
        val w = if (weights.all { it <= 0L }) weights.map { 1L } else weights.map { it.coerceAtLeast(0L) }
        val totalWeight = w.sum()

        val floors = LongArray(w.size)
        val remainders = ArrayList<Pair<Int, Long>>(w.size)
        var assigned = 0L
        for (i in w.indices) {
            val exact = abs * w[i]
            floors[i] = exact / totalWeight
            remainders += i to exact % totalWeight
            assigned += floors[i]
        }
        // Hand out the leftover sen to whoever lost the most to rounding.
        var left = abs - assigned
        for ((i, _) in remainders.sortedByDescending { it.second }) {
            if (left <= 0) break
            floors[i] += 1
            left--
        }
        return floors.map { it * sign }
    }
}
