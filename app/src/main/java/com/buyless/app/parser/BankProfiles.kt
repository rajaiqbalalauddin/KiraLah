package com.buyless.app.parser

import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import com.buyless.app.util.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Exact templates for one bank's alerts, built from real notifications. When a template matches,
 * the result is marked exact and recorded straight away, with no learning period. Anything the
 * templates do not recognise falls back to the generic keyword parser, so a new wording from the
 * bank lands in Quick check instead of being lost.
 */
interface BankProfile {
    /** Returns null when no template matches, so the caller can fall back to the generic parser. */
    fun parse(title: String?, body: String, zone: ZoneId): NotificationParser.Result?
}

object BankProfiles {
    const val BIMB = "bimb"
    const val MAE = "mae"
    const val TNG = "tng"

    // MAE and TNG have no templates yet: their alerts use the generic parser plus learning until
    // real samples are collected (Settings > Notification samples).
    private val profiles: Map<String, BankProfile> = mapOf(BIMB to BimbProfile)

    fun get(id: String?): BankProfile? = id?.let { profiles[it] }
}

/**
 * Bank Islam (BIMB / GO by Bank Islam). Formats taken from the app's inbox, September 2026:
 *
 *  Your QR payment of RM6.50 to Rafa****ures on 21 Sep 2026 06:50:28 PM is successful.
 *  Your FPX payment of RM1,000.00 to PACIFIC TRUSTEES BERHAD on 21 Sep 2026 05:51:29 PM is successful.
 *  Your QR transfer of RM9.90 to HH R****RISE on 23 Sep 2026 07:49:31 PM is successful.
 *  YOU HAVE PERFORMED A TRANSACTION OF RM 18.50 AT KOMUGI-KUCHAI E KUALA LUMPUR ON 20 SEP 26, 16:22:26. ...
 *  YOU HAVE PERFORMED A TRANSACTION OF USD21.60 AT OPENAI* CHATGPT +14158799686 ON 20 SEP 26, 17:44:01. ...
 *  BIMB: RM103.50 received from *Maybank Berhad via DuitNow QR to account **0529 on 20Sep26 12:47:45.
 *  BIMB: PENGGUNAAN KAD **1917 DI JIBBY CHOW (W) KUALA LUMPUR   MY RM206.50, 20Sep26 12:46:42. SILA HUBUNGI ...
 *
 * Titles start with "TRANSACTION -", "PROMOTIONS -" or "UPDATES -"; the last two are never money.
 */
object BimbProfile : BankProfile {

    private val opts = setOf(RegexOption.IGNORE_CASE)
    private const val AMOUNT = """((?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2})"""

    private val notMoneyTitle = Regex("""^\s*(PROMOTIONS?|UPDATES?)\s*-""", opts)

    private val successful = Regex(
        """Your ([A-Za-z ]{2,30}?) of RM\s?$AMOUNT to (.+?) on (\d{1,2} [A-Za-z]{3} \d{4} \d{1,2}:\d{2}:\d{2} [AP]M) is successful""",
        opts,
    )
    private val cardEnglish = Regex(
        """PERFORMED A TRANSACTION OF ([A-Z]{2,3})\s?$AMOUNT AT (.+?) ON (\d{1,2} [A-Za-z]{3} \d{2}), (\d{1,2}:\d{2}:\d{2})""",
        opts,
    )
    private val received = Regex(
        """RM\s?$AMOUNT received from \*?(.+?) via (.+?) to account \S+ on (\d{1,2}[A-Za-z]{3}\d{2}) (\d{1,2}:\d{2}:\d{2})""",
        opts,
    )
    private val cardMalay = Regex(
        """PENGGUNAAN KAD \S+ DI (.+?)\s+(?:[A-Z]{2}\s+)?(RM|MYR|[A-Z]{3})\s?$AMOUNT,\s*(\d{1,2}[A-Za-z]{3}\d{2}) (\d{1,2}:\d{2}:\d{2})""",
        opts,
    )

    private val longDate = formatter("d MMM yyyy h:mm:ss a")
    private val shortDate = formatter("d MMM yy")
    private val compactDate = formatter("dMMMyy")
    private val clock = formatter("H:mm:ss")

    override fun parse(title: String?, body: String, zone: ZoneId): NotificationParser.Result? {
        if (title != null && notMoneyTitle.containsMatchIn(title)) return NotificationParser.Result.Ignore
        val text = if (title.isNullOrBlank()) body else "$title. $body"

        successful.find(text)?.let { m ->
            val kind = m.groupValues[1].trim()
            val merchant = clean(m.groupValues[3])
            val at = dateTime(m.groupValues[4], null, zone)
            return exact(m.groupValues[2], Direction.OUT, merchant, at, text, hint = kind)
        }
        received.find(text)?.let { m ->
            val at = dateTime(m.groupValues[4], m.groupValues[5], zone, compact = true)
            return exact(m.groupValues[1], Direction.IN, clean(m.groupValues[2]), at, text)
        }
        cardEnglish.find(text)?.let { m ->
            val at = dateTime(m.groupValues[4], m.groupValues[5], zone)
            return card(m.groupValues[1], m.groupValues[2], m.groupValues[3], at, text)
        }
        cardMalay.find(text)?.let { m ->
            val at = dateTime(m.groupValues[4], m.groupValues[5], zone, compact = true)
            return card(m.groupValues[2], m.groupValues[3], m.groupValues[1], at, text)
        }
        return null
    }

    /**
     * Card spend. Ringgit is recorded right away. A foreign charge (USD, SGD...) has no ringgit
     * amount yet, since the bank converts it later, so it goes to Quick check with the merchant filled in.
     */
    private fun card(currency: String, amount: String, merchantRaw: String, at: Long?, text: String): NotificationParser.Result {
        val merchant = clean(merchantRaw)
        val isRinggit = currency.equals("RM", true) || currency.equals("MYR", true)
        if (!isRinggit) {
            return NotificationParser.Result.Parsed(
                amountSen = null,
                direction = Direction.OUT,
                merchant = merchant,
                category = NotificationParser.guessCategory(text, merchant, Direction.OUT),
                occurredAt = at,
                exact = false,
                foreign = "${currency.uppercase()} $amount",
            )
        }
        return exact(amount, Direction.OUT, merchant, at, text)
    }

    private fun exact(amount: String, direction: Direction, merchant: String, at: Long?, text: String, hint: String? = null): NotificationParser.Result {
        val sen = Money.parse(amount) ?: return NotificationParser.Result.Ignore
        val category = NotificationParser.guessCategory("$text ${hint.orEmpty()}", merchant, direction)
        return NotificationParser.Result.Parsed(sen, direction, merchant, category, occurredAt = at, exact = true)
    }

    /** "*Maybank Berhad" -> "Maybank Berhad", "OPENAI* CHATGPT +14158799686" -> "Openai* Chatgpt". */
    private fun clean(raw: String): String {
        val trimmed = raw.trim().trimStart('*').replace(Regex("""\s+\+?\d{6,}$"""), "").replace(Regex("""\s{2,}"""), " ").trim()
        return prettify(trimmed)
    }

    /** Title case for all-caps names; masked names like "Kysa****rces" are left as the bank sent them. */
    private fun prettify(name: String): String {
        if (name.any { it.isLowerCase() } || name.contains('*')) return name
        return name.lowercase().split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ENGLISH) } }
    }

    /** Parses the time printed in the alert. Card alerts can arrive 10+ minutes late, so this is more accurate. */
    private fun dateTime(datePart: String, timePart: String?, zone: ZoneId, compact: Boolean = false): Long? = try {
        val local: LocalDateTime = if (timePart == null) {
            LocalDateTime.parse(datePart.trim(), longDate)
        } else {
            val date = LocalDate.parse(datePart.trim(), if (compact) compactDate else shortDate)
            date.atTime(LocalTime.parse(timePart.trim(), clock))
        }
        local.atZone(zone).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }

    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH)
}
