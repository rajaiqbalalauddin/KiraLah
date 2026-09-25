package com.buyless.app.ui.split

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.buyless.app.share.PhoneNumbers
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.buyless.app.split.Share
import com.buyless.app.ui.components.Pill
import com.buyless.app.ui.theme.BColors
import com.buyless.app.util.Money
import kotlin.math.roundToInt

/**
 * Tracks one drag at a time. Positions are in root (window) coordinates so a block can travel
 * from the scrolling item area down to the people tray, which live in different layouts.
 * Bounds maps are plain HashMaps on purpose: they change on every layout pass and nothing needs
 * to recompose when they do. Only pointer and dragging are observable state.
 */
@Stable
private class DragController {
    var draggingId by mutableStateOf<Long?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var grab = Offset.Zero
    var ghostWidthPx = 0f
    var origin = Offset.Zero
    val itemBounds = HashMap<Long, Rect>()
    val targetBounds = HashMap<Long, Rect>()

    fun targetAt(point: Offset): Long? = targetBounds.entries.firstOrNull { it.value.contains(point) }?.key
}

/**
 * The fun part: receipt items as coloured blocks. Hold a block and drag it onto a person (or onto
 * Everyone). For one-handed use and accessibility, tapping a block and then tapping people does the
 * same thing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitBoard(vm: SplitViewModel) {
    val drag = remember { DragController() }
    val haptics = LocalHapticFeedback.current
    val hovered by remember { derivedStateOf { if (drag.draggingId == null) null else drag.targetAt(drag.pointer) } }
    val shares = vm.shares() // recomputed when items or people change; a few dozen items is trivial
    var addingPerson by rememberSaveable { mutableStateOf(false) }
    var editingPerson by rememberSaveable { mutableStateOf<Long?>(null) }
    val left = vm.unassignedCount

    Box(Modifier.fillMaxSize().onGloballyPositioned { drag.origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            StepHeader("Who had what?", onBack = vm::back) {
                if (left > 0) Pill("$left left", BColors.AmberSoft, BColors.AmberInk) else Pill("All sorted", BColors.GreenSoft, BColors.Green)
            }

            AnimatedVisibility(visible = vm.selectedItemId == null && vm.items.none { it.assigned }) {
                Row(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BColors.VioletSoft).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.PanTool, contentDescription = null, tint = BColors.Violet, modifier = Modifier.size(18.dp))
                    Text(
                        "Hold a block and drag it to a person. Or tap a block, then tap people.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            FlowRow(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                vm.items.forEachIndexed { index, item ->
                    ItemBlock(
                        item = item,
                        index = index,
                        people = vm.people,
                        selected = vm.selectedItemId == item.id,
                        beingDragged = drag.draggingId == item.id,
                        modifier = Modifier
                            .onGloballyPositioned { drag.itemBounds[item.id] = it.boundsInRoot() }
                            .pointerInput(item.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { local ->
                                        val bounds = drag.itemBounds[item.id] ?: Rect.Zero
                                        drag.grab = local
                                        drag.ghostWidthPx = bounds.width
                                        drag.pointer = bounds.topLeft + local
                                        drag.draggingId = item.id
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        drag.pointer += amount
                                    },
                                    onDragEnd = {
                                        val target = drag.targetAt(drag.pointer)
                                        if (target != null) {
                                            vm.dropOn(item.id, target)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        drag.draggingId = null
                                    },
                                    onDragCancel = { drag.draggingId = null },
                                )
                            }
                            .clickable(onClickLabel = "Select ${item.name}") { vm.selectItem(item.id) },
                    )
                }
            }

            PeopleTray(
                vm = vm,
                shares = shares,
                hovered = hovered,
                drag = drag,
                onAdd = { addingPerson = true },
                onEdit = { editingPerson = it },
            )
        }

        // The block following your finger. Drawn above everything so it can pass over the tray.
        val draggingItem = vm.items.firstOrNull { it.id == drag.draggingId }
        if (draggingItem != null) {
            val density = LocalDensity.current
            Box(
                Modifier
                    .offset {
                        val p = drag.pointer - drag.grab - drag.origin
                        IntOffset(p.x.roundToInt(), p.y.roundToInt())
                    }
                    .width(with(density) { drag.ghostWidthPx.toDp() })
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        rotationZ = -4f
                    }
                    .shadow(16.dp, RoundedCornerShape(18.dp)),
            ) {
                ItemBlock(draggingItem, vm.items.indexOf(draggingItem), vm.people, selected = false, beingDragged = false, modifier = Modifier)
            }
        }
    }

    if (addingPerson) {
        PeopleSheet(vm, onDismiss = { addingPerson = false })
    }
    editingPerson?.let { id ->
        val person = vm.people.firstOrNull { it.id == id }
        if (person != null) {
            key(id) {
                PersonFormDialog(
                    title = if (id == SplitViewModel.ME_ID) "Your name" else "Edit ${person.name}",
                    initialName = person.name,
                    initialPhone = PhoneNumbers.pretty(person.phone) ?: "",
                    confirm = "Save",
                    showPhone = id != SplitViewModel.ME_ID,
                    onDismiss = { editingPerson = null },
                    onConfirm = { name, phone -> vm.editPerson(id, name, phone); editingPerson = null },
                    removeLabel = "Remove from bill",
                    onRemove = if (id != SplitViewModel.ME_ID) ({ vm.removePerson(id); editingPerson = null }) else null,
                )
            }
        }
    }
}

/** One receipt item. Slightly tilted while unclaimed so the table looks playful, straightens once sorted. */
@Composable
private fun ItemBlock(
    item: BoardItem,
    index: Int,
    people: List<Person>,
    selected: Boolean,
    beingDragged: Boolean,
    modifier: Modifier,
) {
    val tilt by animateFloatAsState(if (item.assigned || selected) 0f else ((index % 5) - 2) * 1.2f, label = "tilt")
    val lift by animateFloatAsState(if (selected) 1.06f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "lift")
    val owners = people.filter { it.id in item.owners }
    val fill = if (item.assigned) BColors.White else BlockFills[index % BlockFills.size]
    val borderColor = when {
        selected -> BColors.Violet
        owners.size == 1 -> PersonColors[owners[0].colorIndex].first
        item.assigned -> BColors.Border
        else -> Color.Transparent
    }

    Column(
        modifier
            .widthIn(min = 104.dp, max = 190.dp)
            .graphicsLayer {
                rotationZ = tilt
                scaleX = lift
                scaleY = lift
                alpha = if (beingDragged) 0.25f else 1f
            }
            .clip(RoundedCornerShape(18.dp))
            .background(fill)
            .border(2.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        item.note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = BColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Text(Money.format(item.priceSen), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        if (owners.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                owners.take(5).forEachIndexed { i, p ->
                    Avatar(p, size = 22.dp, modifier = Modifier.offset(x = (-6 * i).dp), ring = BColors.White)
                }
                if (owners.size > 1) {
                    Text(
                        "${Money.format(item.priceSen / owners.size)} each",
                        style = MaterialTheme.typography.labelMedium,
                        color = BColors.Muted,
                        modifier = Modifier.offset(x = (-6 * minOf(owners.size, 5) + 6).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeopleTray(
    vm: SplitViewModel,
    shares: Map<Long, Share>,
    hovered: Long?,
    drag: DragController,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val selected = vm.items.firstOrNull { it.id == vm.selectedItemId }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(20.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(BColors.White)
            .padding(top = 14.dp),
    ) {
        AnimatedVisibility(visible = selected != null, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut()) {
            if (selected != null) {
                Row(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Tap people for ${selected.name}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.clearOwners(selected.id) }) { Text("Clear") }
                    TextButton(onClick = { vm.selectItem(null) }) { Text("Done") }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(vm.people, key = { it.id }) { person ->
                DropTarget(
                    id = person.id,
                    drag = drag,
                    hovered = hovered == person.id,
                    popKey = vm.lastDrop?.takeIf { it.first == person.id }?.second,
                    checked = selected != null && person.id in selected.owners,
                    onClick = { if (selected != null) vm.toggleOwner(person.id) else onEdit(person.id) },
                    label = person.name,
                    amount = Money.format(shares[person.id]?.totalSen ?: 0L),
                ) { size -> Avatar(person, size) }
            }
            item(key = "everyone") {
                DropTarget(
                    id = SplitViewModel.EVERYONE_ID,
                    drag = drag,
                    hovered = hovered == SplitViewModel.EVERYONE_ID,
                    popKey = vm.lastDrop?.takeIf { it.first == SplitViewModel.EVERYONE_ID }?.second,
                    checked = false,
                    onClick = { if (selected != null) vm.toggleOwner(SplitViewModel.EVERYONE_ID) },
                    label = "Everyone",
                    amount = "Share it",
                ) { size ->
                    Box(Modifier.size(size).clip(CircleShape).background(BColors.Ink), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Groups, contentDescription = null, tint = BColors.White)
                    }
                }
            }
            item(key = "add") {
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "Add a person", onClick = onAdd).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).border(2.dp, BColors.VioletSoft, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = BColors.Violet) }
                    Text("Add", style = MaterialTheme.typography.titleSmall, color = BColors.Violet)
                }
            }
        }

        BottomAction(
            label = "See totals",
            enabled = vm.unassignedCount == 0,
            onClick = vm::toSummary,
            secondary = if (vm.unassignedCount > 0 && vm.items.any { it.assigned }) {
                @Composable {
                    OutlinedButton(onClick = vm::shareLeftovers, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Share the ${vm.unassignedCount} left with everyone", color = BColors.Ink)
                    }
                }
            } else {
                null
            },
        )
    }
}

/** A person bubble that grows when a block hovers over it and pops when something lands on it. */
@Composable
private fun DropTarget(
    id: Long,
    drag: DragController,
    hovered: Boolean,
    popKey: Int?,
    checked: Boolean,
    onClick: () -> Unit,
    label: String,
    amount: String,
    avatar: @Composable (Dp) -> Unit,
) {
    val hoverScale by animateFloatAsState(
        if (hovered) 1.18f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "hover",
    )
    val pop = remember { Animatable(1f) }
    val latestKey by rememberUpdatedState(popKey)
    LaunchedEffect(popKey) {
        if (latestKey != null) {
            pop.snapTo(1.25f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
        }
    }
    DisposableEffect(id) { onDispose { drag.targetBounds.remove(id) } }

    Column(
        Modifier
            .width(76.dp)
            .onGloballyPositioned { drag.targetBounds[id] = it.boundsInRoot() }
            .clip(RoundedCornerShape(16.dp))
            .background(if (hovered) BColors.VioletSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.scale(hoverScale * pop.value), contentAlignment = Alignment.Center) {
            avatar(56.dp)
            if (checked) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(BColors.Green).border(2.dp, BColors.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, contentDescription = "Has this item", tint = BColors.White, modifier = Modifier.size(12.dp)) }
            }
        }
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(amount, style = MaterialTheme.typography.labelMedium, color = BColors.Muted, maxLines = 1)
    }
}

@Composable
internal fun Avatar(person: Person, size: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val (bg, fg) = PersonColors[person.colorIndex % PersonColors.size]
    val initials = person.name.trim().split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials.ifEmpty { "?" },
            color = fg,
            fontWeight = FontWeight.ExtraBold,
            style = if (size > 30.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelMedium,
        )
    }
}

