package com.buyless.app.limits

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.buyless.app.MainActivity
import com.buyless.app.R
import com.buyless.app.data.model.LimitPeriod
import com.buyless.app.util.Money

/**
 * Posts the "limit reached" and "getting close" notifications. Has its own channel, so people can
 * mute limit alerts in Android settings without muting anything else KiraLah may add later.
 */
class LimitNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** True when Android will actually show our notifications (permission granted and not blocked). */
    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    /**
     * One notification per payment, even when it trips several limits, so a single coffee never
     * buzzes the phone three times. [labelOf] turns a category key into its display name.
     */
    @SuppressLint("MissingPermission") // canNotify() checks it, and a late revoke is caught below
    fun post(txId: Long, merchant: String, amountSen: Long, hits: List<LimitHit>, labelOf: (String) -> String) {
        if (hits.isEmpty() || !canNotify()) return
        ensureChannel()

        val lines = hits.map { line(it, labelOf) }
        val first = hits.first()
        // Hits come sorted with reached limits first, so `first` is the most important one.
        val reached = hits.count { it.alert == LimitAlert.REACHED }
        val title = when {
            reached > 1 -> "$reached of your limits reached"
            reached == 1 -> "${name(first, labelOf)} limit reached"
            else -> "${name(first, labelOf)} limit almost used"
        }
        val payment = "${Money.format(amountSen)} at $merchant"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_limit)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setSubText(payment)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        try {
            // Keyed by transaction, so a re-delivered alert for the same payment replaces rather than stacks.
            manager.notify(TAG, txId.toInt(), builder.build())
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the post. Nothing useful to do.
        }
    }

    /** "Monthly Food", "Daily spending" (for the all-categories limit). */
    private fun name(hit: LimitHit, labelOf: (String) -> String): String {
        val limit = hit.status.limit
        val what = labelOf(limit.categoryKey)
        return "${periodAdjective(limit.period)} $what"
    }

    /** "Monthly Food: RM 612.40 of RM 600.00, RM 12.40 over" or "...: RM 490.00 of RM 600.00 (81%)". */
    private fun line(hit: LimitHit, labelOf: (String) -> String): String {
        val s = hit.status
        val head = "${name(hit, labelOf)}: ${Money.format(s.spentSen)} of ${Money.format(s.limit.amountSen)}"
        return when {
            hit.alert == LimitAlert.WARNING -> "$head (${s.percent}%)"
            s.spentSen > s.limit.amountSen -> "$head, ${Money.format(-s.leftSen)} over"
            else -> "$head, nothing left ${periodPhrase(s.limit.period)}"
        }
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Creating a channel that already exists is a no-op, so this is safe to call every time. */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL_ID, "Spending limits", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tells you when a payment goes over, or gets close to, a limit you set"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "spending_limits"
        private const val TAG = "limit"

        fun periodAdjective(p: LimitPeriod) = when (p) {
            LimitPeriod.DAY -> "Daily"
            LimitPeriod.WEEK -> "Weekly"
            LimitPeriod.MONTH -> "Monthly"
        }

        fun periodPhrase(p: LimitPeriod) = when (p) {
            LimitPeriod.DAY -> "today"
            LimitPeriod.WEEK -> "this week"
            LimitPeriod.MONTH -> "this month"
        }
    }
}
