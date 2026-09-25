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
import androidx.compose.runtime.Composable
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
fun PermissionSteps(permissions: PermissionState) {
    val context = LocalContext.current
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
            onAllow = { context.launch(SystemAccess.batteryExemptionIntent(context)) },
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
            .background(BColors.White)
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
                Icon(Icons.Rounded.Check, contentDescription = "Done", tint = BColors.White, modifier = Modifier.size(16.dp))
            }
        } else {
            Button(
                onClick = onAllow,
                colors = ButtonDefaults.buttonColors(containerColor = BColors.Ink, contentColor = BColors.White),
            ) { Text("Allow") }
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
