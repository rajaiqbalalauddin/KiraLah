package com.buyless.app.util

import com.buyless.app.data.model.AppKind
import com.buyless.app.parser.BankProfiles

/**
 * Recognises Malaysian banking and e-wallet apps by label or package name. Matching on the visible
 * label is more robust than hard-coding package ids, which banks change when they relaunch apps.
 */
object KnownApps {

    /** profileId links an app to its exact bank templates in BankProfiles, when there are any. */
    data class Match(val kind: AppKind, val isDefault: Boolean, val profileId: String? = null)

    private class Rule(pattern: String, val kind: AppKind, val isDefault: Boolean = false, val profileId: String? = null) {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
    }

    // Compiled once at class load. The three defaults are the apps Buyless was designed around.
    private val rules = listOf(
        Rule("^MAE\\b|maybank2u\\.life", AppKind.BANK, isDefault = true, profileId = BankProfiles.MAE),
        Rule("bank islam|\\bbimb\\b|bankislam", AppKind.BANK, isDefault = true, profileId = BankProfiles.BIMB),
        Rule("touch ?'?n ?go|\\btng\\b|tngdigital", AppKind.WALLET, isDefault = true, profileId = BankProfiles.TNG),
        Rule("maybank|gxbank|gx bank|aeon bank|boost bank|\\brhb\\b|\\bcimb\\b|\\bocto\\b|public bank|pb engage|hong leong|\\bhlb\\b|ambank|amonline|bank rakyat|\\bbsn\\b|alliance|\\buob\\b|ocbc|hsbc|standard chartered|agrobank|affin|muamalat|bank simpanan", AppKind.BANK),
        Rule("\\bboost\\b|shopeepay|grabpay|\\bgrab\\b|bigpay|setel|mcash|\\bwise\\b|duitnow|lazada wallet|kiplepay", AppKind.WALLET),
    )

    fun match(packageName: String, label: String): Match? {
        for (rule in rules) {
            if (rule.regex.containsMatchIn(label) || rule.regex.containsMatchIn(packageName)) {
                return Match(rule.kind, rule.isDefault, rule.profileId)
            }
        }
        return null
    }
}
