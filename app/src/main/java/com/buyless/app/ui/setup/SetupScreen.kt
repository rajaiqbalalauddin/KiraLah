package com.buyless.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.selection.toggleable
import com.buyless.app.ui.components.AppBadge
import com.buyless.app.ui.components.IconDot
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.theme.BColors

/**
 * First-run screen: explain the idea in one line, get the two permissions, pick apps, go.
 * The Start button stays disabled with a reason until the minimum is met, so the user is never
 * dropped into an app that silently records nothing.
 */
@Composable
fun SetupScreen(onDone: () -> Unit, onAddApp: () -> Unit) {
    val vm = appViewModel { c, _ -> SetupViewModel(c.application, c.apps, c.prefs) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermissions() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box {
                        Box(
                            Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(BColors.Violet),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.NotificationsNone, contentDescription = null, tint = BColors.White, modifier = Modifier.size(30.dp))
                        }
                        Box(
                            Modifier.offset(x = 44.dp, y = (-6).dp).size(22.dp).clip(CircleShape).background(BColors.Yellow),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = BColors.YellowInk, modifier = Modifier.size(14.dp))
                        }
                    }
                    Text("Spending that tracks itself.", style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    "Buyless reads payment alerts from your bank and e-wallet apps and records them for you.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BColors.Muted,
                )
            }
        }

        item(key = "permissions") { PermissionSteps(state.permissions) }

        item(key = "apps") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Apps to watch", style = MaterialTheme.typography.titleMedium)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(BColors.White)
                        .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    if (state.loadingApps) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BColors.Violet, modifier = Modifier.size(24.dp))
                        }
                    }
                    state.apps.forEach { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .toggleable(value = row.checked, role = Role.Checkbox, onValueChange = { vm.toggle(row) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppBadge(row.packageName, row.kind, size = 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(row.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = row.checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = BColors.Violet),
                            )
                        }
                    }
                    if (state.apps.isNotEmpty()) HorizontalDivider(color = BColors.Divider)
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(onClick = onAddApp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconDot(Icons.Rounded.Add, BColors.VioletSoft, BColors.Violet, size = 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Add another app", style = MaterialTheme.typography.titleMedium, color = BColors.Violet)
                    }
                }
            }
        }

        item(key = "privacy") {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BColors.VioletSoft).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = BColors.Violet)
                Text(
                    "Only alerts from these apps are read. Chats and OTPs are ignored. Your data stays on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item(key = "start") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.finish()
                        onDone()
                    },
                    enabled = state.canStart,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BColors.Violet),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Start tracking", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
                if (!state.canStart) {
                    Text(
                        when {
                            !state.permissions.notificationAccess -> "Turn on Notification access to continue."
                            else -> "Pick at least one app to watch."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = BColors.Muted,
                    )
                }
            }
        }
    }
}
