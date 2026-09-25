package com.buyless.app.ui.split

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.ui.components.Pill
import com.buyless.app.ui.components.SwipeToDelete
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.theme.BColors
import com.buyless.app.util.Money
import java.io.File

/** Avatar colours for people at the table: bright, and distinct in lightness as well as hue. */
internal val PersonColors = listOf(
    Color(0xFF5B3DF5) to Color.White,
    Color(0xFFFF6B3D) to Color.White,
    Color(0xFF0FA3A3) to Color.White,
    Color(0xFFFFC940) to Color(0xFF3D2A00),
    Color(0xFF2563EB) to Color.White,
    Color(0xFFE0457B) to Color.White,
    Color(0xFF10B981) to Color(0xFF063B2B),
)

/** Soft fills for item blocks still on the table, so the board looks like a pile of sweets. */
internal val BlockFills = listOf(
    Color(0xFFECE8FF), Color(0xFFFFE6DC), Color(0xFFDDEBFF), Color(0xFFFFF1D6), Color(0xFFD8F5EA), Color(0xFFFFE3EE),
)

/** Entry point of the Split tab. Each step slides in from the side, so the flow feels like one journey. */
@Composable
fun SplitScreen() {
    val vm = appViewModel { c, _ -> SplitViewModel(c.receiptScanner, c.qrs, c.prefs, c.splitHistory, c.friends, c.payCards) }
    BackHandler(enabled = vm.step != SplitStep.SCAN || vm.showingPayFor != null || vm.showingReceipt) { vm.back() }
    val snackbar = remember { SnackbarHostState() }

    Box(Modifier.fillMaxSize()) {
    AnimatedContent(
        targetState = vm.step,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
        },
        label = "splitStep",
    ) { step ->
        when (step) {
            SplitStep.SCAN -> ScanStep(vm, snackbar)
            SplitStep.ITEMS -> ItemsStep(vm)
            SplitStep.BOARD -> SplitBoard(vm)
            SplitStep.SUMMARY -> SplitSummary(vm)
        }
    }

    // The receipt photo opens over whichever step asked for it.
    val path = vm.receiptPath
    if (vm.showingReceipt && path != null) {
        ReceiptViewer(path, vm.merchant ?: "Receipt", onClose = { vm.showReceipt(false) })
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

/** Receipt icon for step headers. Only shown when a photo was kept for this bill. */
@Composable
internal fun ReceiptButton(vm: SplitViewModel) {
    if (vm.receiptPath != null) {
        IconButton(onClick = { vm.showReceipt(true) }) {
            Icon(Icons.Rounded.ReceiptLong, contentDescription = "View receipt photo")
        }
    }
}

// ---------------- Step 1: scan ----------------

@Composable
private fun ScanStep(vm: SplitViewModel, snackbar: SnackbarHostState) {
    val history by vm.historyCards.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    var cameraMissing by rememberSaveable { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        if (ok && uri != null) vm.onPhoto(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onPhoto(uri)
    }

    // While Gemini reads, the whole tab becomes the scanning animation.
    if (vm.scanning) {
        ScanningView(stage = vm.scanStage, photo = vm.scanPhoto, onCancel = vm::cancelScan)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Split", style = MaterialTheme.typography.headlineSmall) }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(BColors.Violet)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row {
                    PersonColors.take(4).forEachIndexed { i, (bg, fg) ->
                        // Overlapping avatars, like friends squeezed round a table.
                        Box(
                            Modifier.offset(x = (-10 * i).dp).size(44.dp).clip(CircleShape).background(bg).border(3.dp, BColors.Violet, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text(listOf("A", "B", "C", "D")[i], color = fg, fontWeight = FontWeight.ExtraBold) }
                    }
                }
                Text("Split the bill, the fun way.", style = MaterialTheme.typography.headlineMedium, color = BColors.White)
                Text(
                    "Snap the receipt. Drag each item to whoever had it. Show your friends the QR to pay you.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BColors.VioletOnDark,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepChip(Icons.Rounded.ReceiptLong, "Scan", BColors.CoralSoft, BColors.CoralInk, Modifier.weight(1f))
                StepChip(Icons.Rounded.Groups, "Drag", BColors.BlueSoft, BColors.BlueInk, Modifier.weight(1f))
                StepChip(Icons.Rounded.QrCode2, "Get paid", BColors.GreenSoft, BColors.Green, Modifier.weight(1f))
            }
        }

        val message = vm.scanMessage ?: if (cameraMissing) "No camera app found. Choose a photo instead." else null
        if (message != null) {
            item { WarningCard(message) }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val uri = newReceiptUri(context)
                        pendingPhoto = uri
                        try {
                            camera.launch(uri)
                        } catch (e: ActivityNotFoundException) {
                            cameraMissing = true
                        }
                    },
                    enabled = !vm.scanning,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BColors.Violet),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Scan receipt", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !vm.scanning,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = BColors.Ink)
                    Spacer(Modifier.width(10.dp))
                    Text("Choose a photo", color = BColors.Ink)
                }
                TextButton(onClick = vm::startManual, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Type the items instead")
                }
            }
        }

        item {
            Text(
                "Tip: lay the receipt flat in good light and fit the whole thing in the photo. " +
                    "The photo is sent to Gemini to read the items, and a copy is kept on this phone with the split.",
                style = MaterialTheme.typography.bodySmall,
                color = BColors.Muted,
            )
        }

        if (history.isNotEmpty()) {
            item(key = "historyHeader") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Past splits", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("Swipe left to delete", style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
                }
            }
            items(history, key = { "h" + it.id }) { card ->
                SwipeToDelete(
                    surface = BColors.Lavender,
                    onDelete = {
                        vm.deleteBill(card.id) { removed ->
                            scope.launch {
                                val r = snackbar.showSnackbar("Deleted ${card.title}", actionLabel = "Undo", duration = SnackbarDuration.Short)
                                if (r == SnackbarResult.ActionPerformed) vm.restoreBill(removed)
                            }
                        }
                    },
                ) {
                    HistoryRow(card, onClick = { vm.openBill(card.id) })
                }
            }
        }
    }
}

/** One past split: receipt thumbnail, name, when, total, and how many friends have paid. */
@Composable
private fun HistoryRow(card: HistoryCard, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BColors.White)
            .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Open ${card.title}", onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReceiptThumb(card.receiptPath)
        Column(Modifier.weight(1f)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.dateText, style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.totalText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Pill(card.paidText, if (card.allPaid) BColors.GreenSoft else BColors.AmberSoft, if (card.allPaid) BColors.Green else BColors.AmberInk)
        }
    }
}

/** A fresh file for each photo, so a retake never shows the previous receipt. */
private fun newReceiptUri(context: Context): Uri {
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "receipt-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun StepChip(icon: ImageVector, label: String, bg: Color, fg: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = fg)
    }
}

@Composable
internal fun WarningCard(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BColors.AmberSoft).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = BColors.Amber)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = BColors.AmberInk)
    }
}

// ---------------- Step 2: check items ----------------

@Composable
private fun ItemsStep(vm: SplitViewModel) {
    Column(Modifier.fillMaxSize()) {
        StepHeader("Check the items", onBack = vm::back) { ReceiptButton(vm) }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    when {
                        vm.editItems.isEmpty() -> "Add what was on the bill."
                        vm.merchant != null -> "${vm.merchant}: found ${vm.editItems.size} items. Fix anything that looks wrong."
                        else -> "Found ${vm.editItems.size} items. Fix anything that looks wrong."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = BColors.Muted,
                )
            }
            vm.scanNotice?.let { notice -> item(key = "notice") { WarningCard(notice) } }
            items(vm.editItems, key = { it.id }) { item ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = item.name,
                        onValueChange = { vm.updateItem(item.id, name = it.take(40)) },
                        placeholder = { Text("Item") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = splitFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = item.priceText,
                        onValueChange = { v -> vm.updateItem(item.id, priceText = v.filter { it.isDigit() || it == '.' }.take(9)) },
                        prefix = { Text("RM ", color = BColors.Muted) },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        isError = item.priceText.isNotEmpty() && item.priceSen == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        colors = splitFieldColors(),
                        modifier = Modifier.width(128.dp),
                    )
                    IconButton(onClick = { vm.removeItem(item.id) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove ${item.name}", tint = BColors.Muted)
                    }
                }
                // Modifiers Gemini found under the item, like "Less ice" or "No onion".
                item.note?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall, color = BColors.Muted, modifier = Modifier.padding(start = 6.dp))
                }
                }
            }
            item {
                TextButton(onClick = vm::addItem) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add item")
                }
            }
            item { ChargesCard(vm) }
            item { TotalCheck(vm) }
        }
        BottomAction(
            label = "Next: who had what?",
            enabled = vm.canContinueToBoard,
            onClick = vm::continueToBoard,
        )
    }
}

@Composable
private fun ChargesCard(vm: SplitViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BColors.White)
            .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Charges", style = MaterialTheme.typography.titleMedium)
        Text(
            "Shared by how much each person ordered, so a small eater pays less service and tax. Tap the tag under a charge if the item prices already include it.",
            style = MaterialTheme.typography.bodySmall,
            color = BColors.Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                ChargeField("Service", vm.serviceText, { vm.serviceText = it }, Modifier.fillMaxWidth())
                if (vm.serviceText.isNotBlank()) IncludedToggle(vm.serviceIncluded) { vm.serviceIncluded = !vm.serviceIncluded }
            }
            Column(Modifier.weight(1f)) {
                ChargeField("SST / tax", vm.taxText, { vm.taxText = it }, Modifier.fillMaxWidth())
                if (vm.taxText.isNotBlank()) IncludedToggle(vm.taxIncluded) { vm.taxIncluded = !vm.taxIncluded }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChargeField("Rounding", vm.roundingText, { vm.roundingText = it }, Modifier.weight(1f), allowMinus = true)
            ChargeField("Discount", vm.discountText, { vm.discountText = it }, Modifier.weight(1f))
        }
    }
}

/**
 * Tells the user whether a charge is added on top of the items or already inside their prices, and lets
 * them flip it. Many Malaysian receipts print SST even though the menu prices already include it.
 */
@Composable
private fun IncludedToggle(included: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (included) BColors.GreenSoft else BColors.VioletSoft)
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (included) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
            contentDescription = null,
            tint = if (included) BColors.Green else BColors.Violet,
            modifier = Modifier.size(14.dp),
        )
        Text(
            if (included) "Already in prices" else "Added on top",
            style = MaterialTheme.typography.labelSmall,
            color = if (included) BColors.Green else BColors.Violet,
        )
    }
}

@Composable
private fun ChargeField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, allowMinus: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            onChange(v.filterIndexed { i, c -> c.isDigit() || c == '.' || (allowMinus && c == '-' && i == 0) }.take(9))
        },
        label = { Text(label) },
        prefix = { Text("RM ", color = BColors.Muted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        colors = splitFieldColors(),
        modifier = modifier,
    )
}

/** Compares our sum with the total printed on the receipt, the quickest way to spot a missed item. */
@Composable
private fun TotalCheck(vm: SplitViewModel) {
    val receipt = vm.receiptTotalSen
    val ours = vm.billTotalSen
    val matches = receipt == null || receipt == ours
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (matches) BColors.GreenSoft else BColors.AmberSoft)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (matches) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = if (matches) BColors.Green else BColors.Amber,
        )
        Column(Modifier.weight(1f)) {
            Text("Bill total ${Money.format(ours)}", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    receipt == null -> "No total found on the receipt to compare with."
                    matches && (vm.taxIncluded || vm.serviceIncluded) ->
                        "Matches the receipt. Prices already include ${if (vm.taxIncluded && vm.serviceIncluded) "service and tax" else if (vm.taxIncluded) "tax" else "service"}, so it is not added again."
                    matches -> "Matches the receipt."
                    else -> "Receipt says ${Money.format(receipt)}. Check for a missed or misread line."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (matches) BColors.Green else BColors.AmberInk,
            )
        }
    }
}

// ---------------- Shared bits ----------------

@Composable
internal fun StepHeader(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
internal fun BottomAction(label: String, enabled: Boolean, onClick: () -> Unit, secondary: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        secondary?.invoke()
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BColors.Violet),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
internal fun splitFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = BColors.White,
    focusedContainerColor = BColors.White,
    unfocusedBorderColor = BColors.Border,
    focusedBorderColor = BColors.Violet,
)
