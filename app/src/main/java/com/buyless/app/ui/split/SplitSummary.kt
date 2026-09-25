package com.buyless.app.ui.split

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buyless.app.container
import com.buyless.app.data.db.PaymentQrEntity
import com.buyless.app.ui.components.Pill
import com.buyless.app.ui.theme.BColors
import com.buyless.app.util.Money
import androidx.compose.ui.platform.LocalContext

/** Step 4: who owes what, which QR friends should pay to, and a full-screen card to show them. */
@Composable
internal fun SplitSummary(vm: SplitViewModel) {
    val shares = vm.shares()
    val qrs by vm.savedQrs.collectAsState()
    var pendingQr by rememberSaveable { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> pendingQr = uri }
    val pickQr = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val friends = vm.people.filter { it.id != SplitViewModel.ME_ID }
    val owedToMe = friends.sumOf { shares[it.id]?.totalSen ?: 0L }
    val collected = friends.filter { it.id in vm.paid }.sumOf { shares[it.id]?.totalSen ?: 0L }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            StepHeader("Totals", onBack = vm::back) {
                IconButton(onClick = vm::startOver) { Icon(Icons.Rounded.Refresh, contentDescription = "Start a new split") }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BColors.Ink).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Friends owe you", style = MaterialTheme.typography.bodyMedium, color = BColors.VioletOnDark)
                        Text(Money.format(owedToMe), style = MaterialTheme.typography.displaySmall, color = BColors.White)
                        Text(
                            "Collected ${Money.format(collected)} · Bill ${Money.format(vm.billTotalSen)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BColors.VioletOnDark,
                        )
                    }
                }

                item { QrPicker(qrs, vm.selectedQrId, vm::selectQr, onAdd = pickQr, onDelete = vm::deleteQr) }

                items(vm.people, key = { it.id }) { person ->
                    PersonTotalCard(
                        person = person,
                        vm = vm,
                        total = shares[person.id]?.totalSen ?: 0L,
                        extras = shares[person.id]?.extrasSen ?: 0L,
                        isPaid = person.id in vm.paid,
                    )
                }
            }
        }

        // Full-screen "show your friend" card, sliding up over the list.
        AnimatedVisibility(
            visible = vm.showingPayFor != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            PayCard(vm, shares.mapValues { it.value.totalSen }, qrs, onAddQr = pickQr)
        }
    }

    pendingQr?.let { uri ->
        LabelQrDialog(
            onDismiss = { pendingQr = null },
            onSave = { label ->
                vm.addQr(uri, label)
                pendingQr = null
            },
        )
    }
}

@Composable
private fun QrPicker(
    qrs: List<PaymentQrEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onDelete: (PaymentQrEntity) -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BColors.White)
            .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Friends pay to", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 14.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(qrs, key = { it.id }) { qr ->
                val chosen = qr.id == selectedId || (selectedId !in qrs.map { it.id } && qr == qrs.first())
                Row(
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (chosen) BColors.Violet else BColors.Lavender)
                        .clickable { onSelect(qr.id) }
                        .padding(start = 14.dp, end = if (chosen) 4.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = if (chosen) BColors.White else BColors.Violet, modifier = Modifier.size(18.dp))
                    Text(qr.label, style = MaterialTheme.typography.titleSmall, color = if (chosen) BColors.White else BColors.Ink)
                    if (chosen) {
                        IconButton(onClick = { confirmDelete = qr.id }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove ${qr.label}", tint = BColors.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            item(key = "add") {
                Row(
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(2.dp, BColors.VioletSoft, RoundedCornerShape(22.dp))
                        .clickable(onClick = onAdd)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = BColors.Violet, modifier = Modifier.size(18.dp))
                    Text(if (qrs.isEmpty()) "Add your payment QR" else "Add QR", style = MaterialTheme.typography.titleSmall, color = BColors.Violet)
                }
            }
        }
        if (qrs.isEmpty()) {
            Text(
                "Save a screenshot of your DuitNow, MAE or TNG QR once. It stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = BColors.Muted,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
    }

    confirmDelete?.let { id ->
        val qr = qrs.firstOrNull { it.id == id }
        if (qr != null) {
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("Remove ${qr.label}?") },
                text = { Text("The saved QR image will be deleted from this phone.") },
                confirmButton = { TextButton(onClick = { onDelete(qr); confirmDelete = null }) { Text("Remove", color = BColors.Danger) } },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
            )
        }
    }
}

@Composable
private fun PersonTotalCard(person: Person, vm: SplitViewModel, total: Long, extras: Long, isPaid: Boolean) {
    val isMe = person.id == SplitViewModel.ME_ID
    val mine = vm.items.filter { person.id in it.owners }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BColors.White)
            .border(2.dp, if (isPaid) BColors.Green else BColors.Border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(person, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(if (isMe) "Your share" else person.name, style = MaterialTheme.typography.titleMedium)
                Text("${mine.size} item${if (mine.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                if (isPaid) Pill("Paid", BColors.GreenSoft, BColors.Green)
            }
        }
        mine.forEach { item ->
            Row {
                Text(
                    if (item.owners.size > 1) "${item.name} (1/${item.owners.size})" else item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(Money.format(item.priceSen / item.owners.size), style = MaterialTheme.typography.bodyMedium, color = BColors.Muted)
            }
        }
        if (extras != 0L) {
            Row {
                Text("Service, tax and rounding", style = MaterialTheme.typography.bodyMedium, color = BColors.Muted, modifier = Modifier.weight(1f))
                Text((if (extras < 0) "-" else "") + Money.format(extras), style = MaterialTheme.typography.bodyMedium, color = BColors.Muted)
            }
        }
        if (!isMe) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.showPay(person.id) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PersonColors[person.colorIndex].first, contentColor = PersonColors[person.colorIndex].second),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Show QR")
                }
                TextButton(onClick = { vm.togglePaid(person.id) }) {
                    Icon(if (isPaid) Icons.Rounded.CheckCircle else Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (isPaid) "Paid" else "Mark paid")
                }
            }
        }
    }
}

/**
 * The card you hand across the table. Background takes the friend's colour so everyone can see whose
 * turn it is, the amount is huge, and "Next" moves to the next person without going back.
 */
@Composable
private fun PayCard(vm: SplitViewModel, totals: Map<Long, Long>, qrs: List<PaymentQrEntity>, onAddQr: () -> Unit) {
    val person = vm.people.firstOrNull { it.id == vm.showingPayFor } ?: return
    val (bg, fg) = PersonColors[person.colorIndex % PersonColors.size]
    val animatedBg by animateColorAsState(bg, label = "payBg")
    val qr = qrs.firstOrNull { it.id == vm.selectedQrId } ?: qrs.firstOrNull()
    val isPaid = person.id in vm.paid

    Column(
        Modifier
            .fillMaxSize()
            .background(animatedBg)
            .pointerInput(Unit) { detectTapGestures { } } // swallow taps so the list underneath is not pressed
            .statusBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Avatar(person, 40.dp, ring = fg)
            Spacer(Modifier.width(10.dp))
            Text("${person.name}, your share", style = MaterialTheme.typography.titleMedium, color = fg, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.showPay(null) }) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = fg) }
        }

        AnimatedContent(targetState = person.id, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "amount") { id ->
            Text(Money.format(totals[id] ?: 0L), color = fg, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(28.dp)).background(BColors.White).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            if (qr == null) {
                Icon(Icons.Rounded.QrCode2, contentDescription = null, tint = BColors.Muted, modifier = Modifier.size(72.dp))
                Text("No payment QR saved yet", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onAddQr, colors = ButtonDefaults.buttonColors(containerColor = BColors.Violet)) { Text("Add my QR") }
            } else {
                QrImage(qr.filePath, Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(1f))
                Text("Scan to pay · ${qr.label}", style = MaterialTheme.typography.titleMedium)
                if (qrs.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(qrs, key = { it.id }) { option ->
                            val chosen = option.id == qr.id
                            Text(
                                option.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (chosen) BColors.White else BColors.Ink,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (chosen) BColors.Ink else BColors.Lavender)
                                    .clickable { vm.selectQr(option.id) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.togglePaid(person.id) },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BColors.White, contentColor = BColors.Ink),
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Icon(if (isPaid) Icons.Rounded.CheckCircle else Icons.Rounded.Check, contentDescription = null, tint = if (isPaid) BColors.Green else BColors.Ink)
                Spacer(Modifier.width(6.dp))
                Text(if (isPaid) "Paid" else "Mark paid")
            }
            if (vm.people.count { it.id != SplitViewModel.ME_ID } > 1) {
                Button(
                    onClick = vm::nextToPay,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BColors.Ink, contentColor = BColors.White),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text("Next person")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

/** Loads the saved QR from the repository's bitmap cache. Nearest-neighbour keeps QR modules crisp. */
@Composable
private fun QrImage(path: String, modifier: Modifier) {
    val repo = LocalContext.current.container.qrs
    val image: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, path) { value = repo.load(path) }
    val bitmap = image
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = "Payment QR code", contentScale = ContentScale.Fit, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.size(48.dp).clip(CircleShape).background(BColors.Lavender))
        }
    }
}

@Composable
private fun LabelQrDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this QR") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("For example MAE, TNG or DuitNow.", style = MaterialTheme.typography.bodyMedium, color = BColors.Muted)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(20) },
                    placeholder = { Text("MAE") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(label.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
