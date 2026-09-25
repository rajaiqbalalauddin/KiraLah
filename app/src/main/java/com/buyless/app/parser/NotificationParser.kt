package com.buyless.app.parser

import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import com.buyless.app.util.Money
import java.time.ZoneId

/**
 * Turns a notification's text into a transaction guess. Pure Kotlin with no Android types, so it can
 * be unit tested on a PC and reused if the parser is ever moved elsewhere.
 *
 * It is keyword based on purpose: banks change their wording often, and a keyword approach degrades
 * gracefully (an unclear alert goes to Quick check) instead of silently failing like an exact template.
 * All regexes are compiled once here, because compiling per notification is the most common hidden
 * cost in parsers like this.
 */
object NotificationParser {

    sealed interface Result {
        /** Not a money notification, or one that must never be stored (OTP, promo). */
        data object Ignore : Result

        /**
         * Best guess. Any field can be null when the text did not make it clear.
         * occurredAt: transaction time printed in the alert, when a bank template could read it.
         * exact: a known bank template matched, so it can be recorded without the learning period.
         * foreign: the original foreign amount ("USD 21.60") when the ringgit amount is not known yet.
         */
        data class Parsed(
            val amountSen: Long?,
            val direction: Direction?,
            val merchant: String?,
            val category: Category,
            val occurredAt: Long? = null,
            val exact: Boolean = false,
            val foreign: String? = null,
        ) : Result {
            val isComplete: Boolean get() = amountSen != null && direction != null
        }
    }

    private val opts = setOf(RegexOption.IGNORE_CASE)

    // "RM12.50", "RM 1,284.60", "MYR 30". Grouped thousands must be tried before plain digits.
    private val amountRegex = Regex(
        """(?:RM|MYR)\s?((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)""",
        opts,
    )

    // Security codes are dropped before anything else so they can never be written to disk.
    private val sensitiveRegex = Regex(
        """\b(OTP|TAC|PIN|password|kata laluan|verification code|kod pengesahan|one[- ]time)\b""",
        opts,
    )

    // Marketing pushes mention ringgit amounts too. These phrases almost never appear in real alerts.
    private val promoRegex = Regex(
        """\b(?:up to|stand a chance|win\b|promo|vouchers?\b|offers?\b|deals?\b|enjoy|limited time|don't miss|get RM|free\b)""",
        opts,
    )

    // Words that only show up once money has actually moved. Their presence overrides promo detection.
    private val confirmRegex = Regex(
        """\b(successful(?:ly)?|berjaya|received|paid|debited|credited|transferred|deducted|diterima|dikreditkan)\b""",
        opts,
    )

    // Strong "money in" words beat "out" words, because "Transfer received" contains both.
    private val strongInRegex = Regex(
        """\b(received|receive|credited|deposited|refund(?:ed)?|cashback|salary|masuk|diterima|terima|dikreditkan)\b""",
        opts,
    )
    private val outRegex = Regex(
        """\b(paid|pay|payment|spent|debited|deducted|purchase[d]?|sent|transferred|transfer|withdrawal|withdrawn|bayar|bayaran|pembayaran|dibayar|pindahan|didebitkan|charged)\b""",
        opts,
    )
    // Weak "in" words, used only when nothing else matched ("Reload successful" on an e-wallet).
    private val weakInRegex = Regex("""\b(reload(?:ed)?|top[- ]?up|topped up)\b""", opts)

    private val merchantOutRegex = Regex(
        """\b(?:to|at|kepada|di)\s+([A-Za-z0-9&'.\- ]{2,40}?)(?=\s+(?:on|at|via|using|for|ref|pada|with|berjaya|successful|is|has|was|telah)\b|[.,!;:]|$)""",
        opts,
    )
    private val merchantInRegex = Regex(
        """\b(?:from|daripada|dari)\s+([A-Za-z0-9&'.\- ]{2,40}?)(?=\s+(?:on|at|via|using|for|ref|pada|with|berjaya|successful|is|has|was|telah)\b|[.,!;:]|$)""",
        opts,
    )

    private val categoryRules: List<Pair<Regex, Category>> = listOf(
        Regex("""toll|plus|parking|petrol|petronas|shell|caltex|bhp|setel|rapid|mrt|lrt|ktm|grab(?!food)|taxi|e-?hailing|touch ?n ?go reload""", opts) to Category.TRANSPORT,
        Regex("""food|restoran|restaurant|cafe|kopi|coffee|mcd|mcdonald|kfc|starbucks|zus|tealive|mamak|nasi|pizza|burger|bakery|foodpanda|grabfood|subway|secret recipe""", opts) to Category.FOOD,
        Regex("""shopee|lazada|mart|speedmart|mydin|aeon|lotus|giant|jaya grocer|watsons|guardian|uniqlo|ikea|mr ?diy|decathlon|store|shop""", opts) to Category.SHOPPING,
        Regex("""baskin|jibby|komugi|dessert|ice cream|boba|chagee|kenny rogers|texas chicken|domino|sushi|dim sum""", opts) to Category.FOOD,
        Regex("""openai|chatgpt|anthropic|claude|netflix|spotify|youtube|google\s?(?:one|play)|apple\.com|icloud|disney|subscription""", opts) to Category.BILLS,
        Regex("""tnb|tenaga|unifi|\btm\b|maxis|celcom|digi|umobile|u mobile|yes 5g|astro|air selangor|syabas|indah water|bill|insurance|takaful|ptptn|loan""", opts) to Category.BILLS,
    )

    /** True for OTP / TAC style messages, which must never be parsed or stored anywhere. */
    fun isSensitive(text: String): Boolean = sensitiveRegex.containsMatchIn(text)

    /**
     * Entry point used by the listener: try the bank's exact templates first, then the generic parser.
     * [profileId] comes from KnownApps, so an app is matched to its bank even if its package name changes.
     */
    fun parse(title: String?, body: String, profileId: String?, zone: ZoneId): Result {
        val combined = listOfNotNull(title?.trim(), body.trim()).filter { it.isNotEmpty() }.joinToString(". ")
        if (combined.isBlank() || isSensitive(combined)) return Result.Ignore
        BankProfiles.get(profileId)?.parse(title, body, zone)?.let { return it }
        return parse(combined)
    }

    fun parse(text: String): Result {
        if (text.isBlank()) return Result.Ignore
        if (sensitiveRegex.containsMatchIn(text)) return Result.Ignore

        val amountSen = amountRegex.find(text)?.groupValues?.get(1)?.let(Money::parse)
        val direction = when {
            strongInRegex.containsMatchIn(text) -> Direction.IN
            outRegex.containsMatchIn(text) -> Direction.OUT
            weakInRegex.containsMatchIn(text) -> Direction.IN
            else -> null
        }

        // Nothing money-like at all: an ordinary app notification.
        if (amountSen == null && direction == null) return Result.Ignore
        // Promotions are dropped unless the text also confirms money actually moved, so
        // "Payment of RM5 successful. Enjoy your meal" is still recorded.
        if (promoRegex.containsMatchIn(text) && !confirmRegex.containsMatchIn(text)) return Result.Ignore

        val merchant = extractMerchant(text, direction)
        val category = guessCategory(text, merchant, direction)
        return Result.Parsed(amountSen, direction, merchant, category)
    }

    private fun extractMerchant(text: String, direction: Direction?): String? {
        val regex = if (direction == Direction.IN) merchantInRegex else merchantOutRegex
        for (match in regex.findAll(text)) {
            val candidate = match.groupValues[1].trim().trimEnd('.', '-')
            // Skip captures that are really amounts, times or account numbers.
            if (candidate.length < 2) continue
            if (!candidate.any { it.isLetter() }) continue
            if (candidate.startsWith("RM", ignoreCase = true) || candidate.startsWith("MYR", ignoreCase = true)) continue
            if (candidate.equals("your account", ignoreCase = true) || candidate.equals("you", ignoreCase = true)) continue
            return candidate
        }
        return null
    }

    fun guessCategory(text: String, merchant: String?, direction: Direction?): Category {
        if (direction == Direction.IN) return Category.INCOME
        val haystack = if (merchant != null) "$merchant $text" else text
        for ((regex, category) in categoryRules) {
            if (regex.containsMatchIn(haystack)) return category
        }
        return Category.OTHER
    }
}
