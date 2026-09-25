package com.buyless.app.ui.apps

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.ui.components.AppBadge
import com.buyless.app.ui.components.EmptyState
import com.buyless.app.ui.components.IconDot
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.theme.BColors

/**
 * Choose which apps to watch. Money apps found on the phone come first; everything else is behind a
 * toggle or search so a phone with 150 apps does not bury the useful ones.
 */
@Composable
fun AppsScreen(onBack: (() -> Unit)? = null) {
    val vm = appViewModel { c, _ -> AppsViewModel(c.apps) }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var showOthers by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                }
                Text(
                    if (onBack != null) "Add an app" else "Apps",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }

        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search apps on this phone") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = BColors.White,
                    focusedContainerColor = BColors.White,
                    unfocusedBorderColor = BColors.Border,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.loading) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BColors.Violet)
                }
            }
            return@LazyColumn
        }

        val results = state.results
        if (results != null) {
            if (results.isEmpty()) {
                item(key = "noResults") { EmptyState(Icons.Rounded.SearchOff, "No app found", "Check the spelling, or look under Other apps.") }
            } else {
                groupCard("results", "Search results", results, vm::toggle)
            }
            return@LazyColumn
        }

        if (state.watching.isNotEmpty()) groupCard("watching", "Watching", state.watching, vm::toggle)
        if (state.suggestions.isNotEmpty()) groupCard("suggested", "Money apps on your phone", state.suggestions, vm::toggle)

        item(key = "othersToggle") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BColors.White)
                    .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
                    .clickable { showOthers = !showOthers }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconDot(Icons.Rounded.Apps, BColors.VioletSoft, BColors.Violet, size = 38.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Other apps", style = MaterialTheme.typography.titleMedium)
                    Text("Any app that sends payment alerts", style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
                }
                Icon(if (showOthers) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
        }
        if (showOthers) {
            items(state.others, key = { "o" + it.packageName }, contentType = { "row" }) { row ->
                AppRow(row, onToggle = { vm.toggle(row) }, modifier = Modifier.padding(horizontal = 14.dp))
            }
        }

        item(key = "learningInfo") {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BColors.AmberSoft).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = BColors.Amber)
                Text(
                    "New apps learn from you. Their first few alerts go to Quick check. Once you confirm " +
                        "3 correct guesses, Buyless records the rest by itself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BColors.AmberInk,
                )
            }
        }
    }
}

/** A titled white card holding app rows. Emitted as one item so the card border stays intact. */
private fun LazyListScope.groupCard(key: String, title: String, rows: List<AppRowUi>, onToggle: (AppRowUi) -> Unit) {
    item(key = key) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = BColors.Muted)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(BColors.White)
                    .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                rows.forEach { row -> AppRow(row, onToggle = { onToggle(row) }) }
            }
        }
    }
}

@Composable
private fun AppRow(row: AppRowUi, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppBadge(row.packageName, row.kind, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (row.watched) FontWeight.SemiBold else FontWeight.Normal,
                color = if (row.learning) BColors.Violet else BColors.Muted,
            )
        }
        ToggleButton(row.watched, row.label, onToggle)
    }
}

/** Add / Added button. The label changes with state so the action is clear without colour. */
@Composable
private fun ToggleButton(watched: Boolean, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .defaultMinSize(minWidth = 84.dp, minHeight = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (watched) BColors.VioletSoft else BColors.Ink)
            .clickable(onClickLabel = if (watched) "Stop watching $label" else "Watch $label", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (watched) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = BColors.VioletDark, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            if (watched) "Added" else "Add",
            style = MaterialTheme.typography.titleSmall,
            color = if (watched) BColors.VioletDark else BColors.White,
        )
    }
}
