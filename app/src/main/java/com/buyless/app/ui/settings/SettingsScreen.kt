package com.buyless.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.data.model.LEARNING_THRESHOLD
import com.buyless.app.ui.components.IconDot
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.setup.PermissionSteps
import com.buyless.app.ui.setup.SetupViewModel
import com.buyless.app.ui.theme.BColors

/** Health check for tracking (are both permissions still on?) plus the privacy and learning notes. */
@Composable
fun SettingsScreen(onOpenApps: () -> Unit, onOpenSamples: () -> Unit) {
    // Reuses the Setup logic for permission status, so both screens always agree.
    val vm = appViewModel { c, _ -> SetupViewModel(c.application, c.apps, c.prefs) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermissions() }
    val watchedCount = state.apps.count { it.checked }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp)) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.permissions.notificationAccess) "Tracking is on" else "Tracking is off",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.permissions.notificationAccess) BColors.Green else BColors.Danger,
                )
                PermissionSteps(state.permissions)
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BColors.White)
                    .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenApps)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconDot(Icons.Rounded.Apps, BColors.VioletSoft, BColors.Violet, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Watched apps", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (watchedCount == 1) "1 app" else "$watchedCount apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = BColors.Muted,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = BColors.Muted)
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BColors.White)
                    .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenSamples)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconDot(Icons.Rounded.Inbox, BColors.AmberSoft, BColors.Amber, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Notification samples", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "See what MAE, TNG and others send, and share alerts Buyless did not recognise",
                        style = MaterialTheme.typography.bodySmall,
                        color = BColors.Muted,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = BColors.Muted)
            }
        }

        item {
            InfoCard(
                Icons.Rounded.Lightbulb, BColors.AmberSoft, BColors.Amber, BColors.AmberInk,
                "How learning works",
                "Each app's first alerts go to Quick check. After $LEARNING_THRESHOLD guesses you confirm without " +
                    "changes, that app is recorded automatically. Anything unclear still comes to you.",
            )
        }

        item {
            InfoCard(
                Icons.Rounded.VerifiedUser, BColors.VioletSoft, BColors.Violet, BColors.Ink,
                "Your data stays here",
                "Everything is stored only on this phone. Buyless has no account and no server. " +
                    "One-time codes (OTP / TAC) are never saved.",
            )
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, bg: Color, tint: Color, textColor: Color, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = textColor)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = textColor)
        }
    }
}
