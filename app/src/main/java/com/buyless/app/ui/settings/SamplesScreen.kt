package com.buyless.app.ui.settings

import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.buyless.app.data.db.RawNotificationEntity
import com.buyless.app.data.repo.SampleRepository
import com.buyless.app.ui.components.EmptyState
import com.buyless.app.ui.components.Pill
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.theme.BColors
import com.buyless.app.util.AppPrefs
import com.buyless.app.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the sample list and the collect switch. */
class SamplesViewModel(private val samples: SampleRepository, private val prefs: AppPrefs) : ViewModel() {
    val all: StateFlow<List<RawNotificationEntity>> = samples.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var collecting by mutableStateOf(prefs.collectSamples)
        private set

    fun updateCollecting(on: Boolean) {
        collecting = on
        prefs.collectSamples = on
    }

    fun clear() {
        viewModelScope.launch { samples.clear() }
    }

    fun export(list: List<RawNotificationEntity>): String = samples.exportText(list)
}

/**
 * "Listen first" screen: shows what each watched app actually sends. Items marked "Not recognised"
 * are the ones worth sharing, so exact templates can be written for that bank.
 */
@Composable
fun SamplesScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> SamplesViewModel(c.samples, c.prefs) }
    val all by vm.all.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyUnknown by rememberSaveable { mutableStateOf(true) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val apps = all.map { it.sourceLabel }.distinct()
    val shown = all.filter { (filter == null || it.sourceLabel == filter) && (!onlyUnknown || !it.matched) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text("Notification samples", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmClear = true }, enabled = all.isNotEmpty()) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear samples")
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(BColors.White)
                        .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Collect samples", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Keeps the last 300 alerts from watched apps on this phone. OTP and TAC messages are never kept.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BColors.Muted,
                        )
                    }
                    Switch(
                        checked = vm.collecting,
                        onCheckedChange = vm::updateCollecting,
                        colors = SwitchDefaults.colors(checkedTrackColor = BColors.Violet),
                    )
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterText("Not recognised", onlyUnknown) { onlyUnknown = !onlyUnknown } }
                    item { FilterText("All apps", filter == null) { filter = null } }
                    items(apps) { label -> FilterText(label, filter == label) { filter = label } }
                }
            }
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Inbox,
                        if (all.isEmpty()) "No samples yet" else "Nothing here",
                        if (all.isEmpty()) "Use MAE or TNG as usual. Their alerts will show up here." else "Every alert in this view was recognised.",
                    )
                }
            }
            items(shown, key = { it.id }) { s ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BColors.White)
                        .border(1.dp, BColors.Border, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${s.sourceLabel} · ${Dates.relativeDay(Dates.toDate(s.postedAt))}, ${Dates.timeOf(s.postedAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = BColors.Muted,
                            modifier = Modifier.weight(1f),
                        )
                        if (s.matched) Pill("Recognised", BColors.GreenSoft, BColors.Green) else Pill("Not recognised", BColors.AmberSoft, BColors.AmberInk)
                    }
                    s.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    Text(s.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Button(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.export(shown))
                context.startActivity(Intent.createChooser(send, "Share samples"))
            },
            enabled = shown.isNotEmpty(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BColors.Violet),
            modifier = Modifier.fillMaxWidth().padding(20.dp).height(56.dp),
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share ${shown.size} samples", style = MaterialTheme.typography.labelLarge)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all samples?") },
            text = { Text("Your recorded transactions are not affected.") },
            confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text("Clear", color = BColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FilterText(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = if (selected) BColors.White else BColors.Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) BColors.Ink else BColors.White)
            .border(1.dp, if (selected) BColors.Ink else BColors.Border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
