package com.buyless.app.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.buyless.app.container
import com.buyless.app.data.db.PendingEntity
import com.buyless.app.data.db.RawNotificationEntity
import com.buyless.app.data.db.WatchedAppEntity
import com.buyless.app.data.model.Category
import com.buyless.app.data.model.LEARNING_THRESHOLD
import com.buyless.app.data.model.PendingStatus
import com.buyless.app.data.model.TransactionDraft
import com.buyless.app.parser.NotificationParser
import com.buyless.app.util.Dates
import com.buyless.app.util.KnownApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives every notification on the phone once Notification access is granted.
 *
 * Efficiency matters here because this runs for every WhatsApp message, email and game alert:
 * - The watched-app list is held in memory as a HashMap, so an unrelated notification costs one
 *   lookup on the main thread and nothing else.
 * - Only watched notifications are copied out and parsed, on a background dispatcher.
 * - The database is only touched for real money alerts.
 */
class PaymentListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null

    @Volatile
    private var watched: Map<String, WatchedAppEntity> = emptyMap()

    /** Plain copy of the parts we need, so the StatusBarNotification is not held across threads. */
    private class Snapshot(
        val packageName: String,
        val key: String,
        val postedAt: Long,
        val title: String?,
        val body: String,
        val text: String,
    )

    /** Bank template id per package ("" = none), looked up once per app instead of per notification. */
    private val profiles = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        watchJob?.cancel()
        watchJob = scope.launch {
            var firstLoad = true
            applicationContext.container.apps.observeWatched().collect { list ->
                watched = list.associateBy { it.packageName }
                if (firstLoad) {
                    firstLoad = false
                    catchUp()
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        watchJob?.cancel()
        // Ask Android to reconnect us, otherwise tracking silently stops until the next reboot.
        NotificationListenerService.requestRebind(ComponentName(this, PaymentListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val app = watched[sbn.packageName] ?: return // fast path for the 99% we ignore
        val snapshot = snapshotOf(sbn) ?: return
        scope.launch { handle(app, snapshot) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Alerts that arrived while the listener was down may still sit in the notification shade.
     * Re-reading them on connect recovers those; dedupKey makes this safe to repeat.
     */
    private suspend fun catchUp() {
        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            null
        } ?: return
        for (sbn in active) {
            val app = watched[sbn.packageName] ?: continue
            val snapshot = snapshotOf(sbn) ?: continue
            handle(app, snapshot)
        }
    }

    private fun snapshotOf(sbn: StatusBarNotification): Snapshot? {
        val n = sbn.notification ?: return null
        // Group summaries repeat their children's content, and ongoing ones are progress bars etc.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return null
        val extras = n.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        val text = listOfNotNull(title, body.takeIf { it.isNotEmpty() }).joinToString(". ")
        if (text.isEmpty()) return null
        return Snapshot(sbn.packageName, sbn.key, sbn.postTime, title, body, text)
    }

    private suspend fun handle(app: WatchedAppEntity, s: Snapshot) {
        val container = applicationContext.container
        val profileId = profiles.getOrPut(app.packageName) { KnownApps.match(app.packageName, app.label)?.profileId.orEmpty() }
            .ifEmpty { null }
        val result = NotificationParser.parse(s.title, s.body, profileId, Dates.zone)

        // Keep a sample of everything a watched app sends (except OTPs), so new formats can be learnt.
        if (container.prefs.collectSamples && !NotificationParser.isSensitive(s.text)) {
            container.samples.record(
                RawNotificationEntity(
                    sourcePackage = app.packageName,
                    sourceLabel = app.label,
                    title = s.title,
                    body = s.body,
                    postedAt = s.postedAt,
                    matched = (result as? NotificationParser.Result.Parsed)?.exact == true,
                ),
            )
        }
        if (result !is NotificationParser.Result.Parsed) return

        // Same notification key + same text within the same minute = the same alert re-posted.
        val dedupKey = "${s.packageName}|${s.key}|${s.text.hashCode()}|${s.postedAt / 60_000}"
        val repo = container.transactions
        // An exact bank template is trusted right away; anything else earns trust through confirmations.
        val trusted = result.exact || app.confirmedCount >= LEARNING_THRESHOLD
        // Prefer the time printed in the alert (card alerts can arrive late), unless it looks wrong.
        val at = result.occurredAt?.takeIf { it <= s.postedAt + CLOCK_SKEW_MS && s.postedAt - it < MAX_DELAY_MS } ?: s.postedAt

        if (trusted && result.isComplete) {
            repo.recordAuto(
                TransactionDraft(
                    amountSen = result.amountSen!!,
                    direction = result.direction!!,
                    merchant = result.merchant ?: app.label,
                    category = result.category,
                    sourcePackage = app.packageName,
                    sourceLabel = app.label,
                    timestamp = at,
                    rawText = s.text,
                ),
                dedupKey,
            )
        } else {
            repo.addPending(
                PendingEntity(
                    sourcePackage = app.packageName,
                    sourceLabel = app.label,
                    text = s.text,
                    postedAt = at,
                    dedupKey = dedupKey,
                    status = PendingStatus.OPEN.name,
                    guessAmountSen = result.amountSen,
                    guessDirection = result.direction?.name,
                    guessMerchant = result.merchant,
                    guessCategory = result.category.takeIf { it != Category.OTHER }?.name,
                ),
            )
        }
    }

    private companion object {
        const val CLOCK_SKEW_MS = 5 * 60 * 1000L
        const val MAX_DELAY_MS = 3 * 24 * 60 * 60 * 1000L
    }
}
