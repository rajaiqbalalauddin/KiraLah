package com.buyless.app.ui.limits

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.container
import com.buyless.app.data.model.ALL_CATEGORIES
import com.buyless.app.limits.LimitNotifier
import com.buyless.app.limits.LimitPeriods
import com.buyless.app.limits.LimitStatus
import com.buyless.app.ui.categories.CategoryCatalog
import com.buyless.app.ui.categories.LocalCategoryCatalog
import com.buyless.app.ui.components.CategoryBadge
import com.buyless.app.ui.components.CategoryStyle
import com.buyless.app.ui.components.IconDot
import com.buyless.app.ui.components.SectionHeader
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.theme.BColors
import com.buyless.app.util.Money

/** Id meaning "a new limit" in the sheet. Real ids start at 1. */
private const val NEW_LIMIT = 0L

/**
 * The Limits block on the Activity tab: a header with Add, one card per limit showing how much is
 * used in the current day, week or month, and the add/edit sheet. Lives on Activity (not Settings)
 * because limits are about spending, and that is where you already look at what you spent.
 * Always shows the current period, whichever month the rest of Activity is browsing.
 */
@Composable
fun LimitsPanel(modifier: Modifier = Modifier) {
    val vm = appViewModel { c, _ -> LimitsViewModel(c.limits) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifier = remember { context.container.limitNotifier }
    var canNotify by remember { mutableStateOf(notifier.canNotify()) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Re-checked on resume, so coming back from Android's notification settings updates the banner.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canNotify = notifier.canNotify() }

    // A denial from the banner opens Android settings, since the system dialog will not show again.
    var fromBanner by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = notifier.canNotify()
        if (!granted && fromBanner) openNotificationSettings(context)
        fromBanner = false
    }
    fun askForNotifications(banner: Boolean) {
        if (notifier.canNotify()) return
        fromBanner = banner
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (banner) openNotificationSettings(context)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Limits") {
            TextButton(onClick = { editingId = NEW_LIMIT }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        if (!canNotify && state.statuses.isNotEmpty()) {
            NotificationsOffBanner(onTurnOn = { askForNotifications(banner = true) })
        }

        if (state.loaded && state.statuses.isEmpty()) {
            NoLimitsCard(onClick = { editingId = NEW_LIMIT })
        }

        state.statuses.forEach { status ->
            key(status.limit.id) { LimitCard(status, onClick = { editingId = status.limit.id }) }
        }
    }

    val id = editingId
    if (id != null) {
        val existing = state.statuses.firstOrNull { it.limit.id == id }?.limit
        // Editing a limit whose row has not loaded yet: wait a frame rather than showing "New".
        if (id == NEW_LIMIT || existing != null) {
            LimitSheet(
                existing = existing,
                taken = state.statuses.map { it.limit }.filter { it.id != id }.map { it.categoryKey to it.period }.toSet(),
                onDismiss = { editingId = null },
                onSave = { period, key, amount ->
                    vm.save(existing, period, key, amount)
                    editingId = null
                    askForNotifications(banner = false)
                },
                onDelete = existing?.let { e -> { vm.delete(e.id); editingId = null } },
            )
        }
    }
}

/** Empty state that is itself the button, so setting the first limit is one tap. */
@Composable
private fun NoLimitsCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BColors.Surface)
            .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Add limit", onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDot(Icons.Rounded.Savings, BColors.GreenSoft, BColors.Green, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Set a spending limit", style = MaterialTheme.typography.titleMedium)
            Text(
                "Daily, weekly or monthly, for one category or everything. You get a reminder at " +
                    "${LimitPeriods.WARNING_PERCENT}% and on every payment once it is used up.",
                style = MaterialTheme.typography.bodySmall,
                color = BColors.Muted,
            )
        }
    }
}

/** How a limit's category looks: the real category badge, or an "all" badge for every category. */
fun limitStyle(key: String, catalog: CategoryCatalog): CategoryStyle =
    if (key == ALL_CATEGORIES) CategoryStyle("All spending", Icons.Rounded.AllInclusive, BColors.VioletSoft, BColors.Violet)
    else catalog.style(key)

@Composable
private fun LimitCard(status: LimitStatus, onClick: () -> Unit) {
    val limit = status.limit
    val style = limitStyle(limit.categoryKey, LocalCategoryCatalog.current)
    // Violet while fine, amber from the warning point, red once reached. Text says the same thing,
    // so the meaning never rests on colour alone.
    val barColor: Color = when {
        status.reached -> BColors.Danger
        status.percent >= LimitPeriods.WARNING_PERCENT -> BColors.Amber
        else -> BColors.Violet
    }
    val span = LimitNotifier.periodPhrase(limit.period)
    val note = when {
        status.spentSen > limit.amountSen -> "${Money.format(-status.leftSen)} over $span"
        status.reached -> "Used up $span"
        else -> "${Money.format(status.leftSen)} left $span"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BColors.Surface)
            .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Edit limit", onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(style, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(style.label, style = MaterialTheme.typography.titleMedium)
                Text("${LimitNotifier.periodAdjective(limit.period)} limit", style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(status.spentSen), style = MaterialTheme.typography.titleMedium, color = if (status.reached) BColors.Danger else BColors.Ink)
                Text("of ${Money.format(limit.amountSen)}", style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
            }
        }
        LinearProgressIndicator(
            progress = { status.fraction.coerceIn(0f, 1f) },
            color = barColor,
            trackColor = BColors.Lavender,
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Text(note, style = MaterialTheme.typography.bodySmall, color = if (status.reached) BColors.Danger else BColors.Muted)
    }
}

/** Shown when limits exist but Android would hide the reminders, so the feature never fails silently. */
@Composable
private fun NotificationsOffBanner(onTurnOn: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BColors.AmberSoft).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = BColors.Amber)
        Spacer(Modifier.width(10.dp))
        Text(
            "Notifications are off, so limits cannot remind you.",
            style = MaterialTheme.typography.bodyMedium,
            color = BColors.AmberInk,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onTurnOn) { Text("Turn on", color = BColors.AmberInk) }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
