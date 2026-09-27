package com.buyless.app.ui.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.buyless.app.ui.theme.BColors
import com.buyless.app.util.SystemAccess

/**
 * The two system permissions as tappable steps. Shared by Setup and Settings so both always agree.
 * Each step shows a tick when done, or a button that jumps straight to the right system page.
 */
@Composable
fun PermissionSteps(permissions: PermissionState, onRefresh: () -> Unit) {
    val context = LocalContext.current
    RefreshOnReturn(onRefresh)
    // The battery prompt is a small system dialog over our screen. Launching it for a result gives a
    // callback the moment it closes, instead of relying on ON_RESUME, which some phones never send for it.
    val batteryPrompt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onRefresh() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepCard(
            icon = Icons.Rounded.NotificationsActive,
            iconBg = BColors.VioletSoft,
            iconFg = BColors.Violet,
            title = "Notification access",
            body = "Lets KiraLah see payment alerts as they arrive.",
            done = permissions.notificationAccess,
            onAllow = { context.launch(SystemAccess.notificationAccessIntent()) },
        )
        if (!permissions.notificationAccess) {
            // Android 13+ blocks this switch for sideloaded apps until "restricted settings" is allowed.
            TextButton(onClick = { context.launch(SystemAccess.appInfoIntent(context)) }) {
                Text("Switch greyed out? Open App info, tap the menu, then Allow restricted settings.")
            }
        }
        StepCard(
            icon = Icons.Rounded.BatteryChargingFull,
            iconBg = BColors.AmberSoft,
            iconFg = BColors.Amber,
            title = "Keep running",
            body = "Stops your phone from closing KiraLah in the background. Recommended.",
            done = permissions.batteryExempt,
            onAllow = {
                try {
                    batteryPrompt.launch(SystemAccess.batteryExemptionIntent(context))
                } catch (e: ActivityNotFoundException) {
                    context.launch(SystemAccess.appInfoIntent(context))
                }
            },
        )
    }
}

@Composable
private fun StepCard(
    icon: ImageVector,
    iconBg: Color,
    iconFg: Color,
    title: String,
    body: String,
    done: Boolean,
    onAllow: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BColors.Surface)
            .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconFg, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
        }
        if (done) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(BColors.Green),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "Done", tint = BColors.OnColor, modifier = Modifier.size(16.dp))
            }
        } else {
            Button(
                onClick = onAllow,
                colors = ButtonDefaults.buttonColors(containerColor = BColors.Ink, contentColor = BColors.Surface),
            ) { Text("Allow") }
        }
    }
}

/**
 * Re-checks permissions whenever our window gets focus back, which also happens when a system dialog
 * closes without pausing the app. The later re-checks cover phones that save the "Allow" a moment
 * after the dialog is gone; without them the step kept showing Allow after it was granted.
 */
@Composable
private fun RefreshOnReturn(onRefresh: () -> Unit) {
    val windowInfo = LocalWindowInfo.current
    val refresh by rememberUpdatedState(onRefresh)
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .filter { it }
            .collectLatest {
                refresh()
                delay(600)
                refresh()
                delay(1_500)
                refresh()
            }
    }
}

/** Some OEM ROMs remove certain settings pages. Fail quietly instead of crashing. */
private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        startActivity(SystemAccess.appInfoIntent(this))
    }
}
