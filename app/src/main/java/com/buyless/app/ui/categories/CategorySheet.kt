package com.buyless.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.data.db.CustomCategoryEntity
import com.buyless.app.data.model.CategoryKeys
import com.buyless.app.ui.components.CategoryBadge
import com.buyless.app.ui.components.CategoryStyle
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.theme.BColors

/** Id meaning "a new category" in [CategoryEditing]. Real ids start at 1. */
const val NEW_CATEGORY = 0L

/**
 * Opens the category sheet for a new category (id = NEW_CATEGORY) or an existing one, and handles
 * the delete confirmation. Both the editor and the Settings page use this, so creating a category
 * works the same everywhere. onCreated receives the new key so the editor can select it.
 */
@Composable
fun CategoryEditing(id: Long?, onDone: () -> Unit, onCreated: (String) -> Unit = {}, onDeleted: (String) -> Unit = {}) {
    if (id == null) return
    val vm = appViewModel { c, _ -> CategoriesViewModel(c.categories) }
    val custom by vm.custom.collectAsStateWithLifecycle()
    val existing = custom.firstOrNull { it.id == id }
    // Editing a category whose row has not loaded yet: wait a frame rather than showing "New".
    if (id != NEW_CATEGORY && existing == null) return

    var confirmDelete by remember(id) { mutableStateOf(false) }
    CategorySheet(
        existing = existing,
        defaultColor = custom.size,
        onDismiss = onDone,
        onSave = { name, icon, color ->
            vm.save(existing, name, icon, color, onCreated)
            onDone()
        },
        onDelete = if (existing != null) ({ confirmDelete = true }) else null,
    )

    if (confirmDelete && existing != null) {
        var count by remember(existing.id) { mutableStateOf<Int?>(null) }
        LaunchedEffect(existing.id) { count = vm.usageCount(existing.id) }
        val used = count ?: 0
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.name}?") },
            text = {
                Text(
                    when (used) {
                        0 -> "No transactions use it."
                        1 -> "1 transaction will move to Other (or Income for money in)."
                        else -> "$used transactions will move to Other (or Income for money in)."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(existing.id)
                    onDeleted(CategoryKeys.custom(existing.id))
                    confirmDelete = false
                    onDone()
                }) { Text("Delete", color = BColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Name, colour and icon for a category, with a live preview of the badge. The icon grid is
 * grouped (Food & drink, Transport...) and searchable, because 200+ icons in one flat grid is
 * too many to scan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(
    existing: CustomCategoryEntity?,
    defaultColor: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, iconKey: String, colorIndex: Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var iconKey by rememberSaveable(existing?.id) { mutableStateOf(existing?.iconKey ?: DEFAULT_ICON) }
    var colorIndex by rememberSaveable(existing?.id) { mutableStateOf(existing?.colorIndex ?: defaultColor.mod(CategoryColors.size)) }
    var query by rememberSaveable { mutableStateOf("") }
    val color = categoryColor(colorIndex)
    val preview = CategoryStyle(name.ifBlank { "New category" }, categoryIcon(iconKey), color.bg, color.fg)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = BColors.Surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (existing == null) "New category" else "Edit category", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = BColors.Danger)
                        Spacer(Modifier.size(4.dp))
                        Text("Delete", color = BColors.Danger)
                    }
                }
            }

            // Preview + name: what you type is what the chip will say.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryBadge(preview, size = 52.dp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME) },
                    placeholder = { Text("Name, like Kucing or Tuition") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(14.dp),
                    colors = sheetFieldColors(),
                    modifier = Modifier.weight(1f),
                )
            }

            Text("Colour", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(CategoryColors) { index, c ->
                    val chosen = index == colorIndex
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c.bg)
                            .border(if (chosen) 3.dp else 1.dp, if (chosen) c.fg else BColors.Border, CircleShape)
                            .clickable(role = Role.RadioButton) { colorIndex = index }
                            .semantics { selected = chosen; contentDescription = "Colour ${index + 1}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (chosen) Icon(Icons.Rounded.Check, contentDescription = null, tint = c.fg, modifier = Modifier.size(18.dp))
                        else Box(Modifier.size(14.dp).clip(CircleShape).background(c.fg))
                    }
                }
            }

            Text("Icon", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(30) },
                placeholder = { Text("Search icons: coffee, petrol, cat...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = sheetFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            IconGrid(query = query, selectedKey = iconKey, color = color, onPick = { iconKey = it })

            Button(
                onClick = { onSave(name.trim(), iconKey, colorIndex) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BColors.Violet, contentColor = BColors.OnColor),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (existing == null) "Create category" else "Save changes") }
        }
    }
}

/** Grouped, searchable icon grid. Fixed height so the sheet keeps its buttons on screen. */
@Composable
private fun IconGrid(query: String, selectedKey: String, color: CategoryColor, onPick: (String) -> Unit) {
    val terms = remember(query) { query.lowercase().split(' ').filter { it.isNotBlank() } }
    val groups = remember(terms) {
        if (terms.isEmpty()) {
            CategoryIconGroups
        } else {
            CategoryIconGroups.mapNotNull { g ->
                val hits = g.icons.filter { (key, _) -> val text = iconSearchText[key].orEmpty(); terms.all { it in text } }
                if (hits.isEmpty()) null else g.copy(icons = hits)
            }
        }
    }

    if (groups.isEmpty()) {
        Text("No icons match. Try a simpler word.", style = MaterialTheme.typography.bodyMedium, color = BColors.Muted, modifier = Modifier.padding(vertical = 24.dp))
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(52.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().height(280.dp),
    ) {
        groups.forEach { group ->
            item(key = "h-${group.title}", span = { GridItemSpan(maxLineSpan) }) {
                Text(group.title, style = MaterialTheme.typography.labelMedium, color = BColors.Muted, modifier = Modifier.padding(top = 6.dp))
            }
            items(group.icons, key = { it.first }) { (key, icon) ->
                val chosen = key == selectedKey
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (chosen) color.bg else BColors.Lavender)
                        .border(2.dp, if (chosen) color.fg else BColors.Lavender, RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { onPick(key) }
                        .semantics { selected = chosen; contentDescription = readableName(key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = if (chosen) color.fg else BColors.Ink, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** "LocalCafe" -> "Local cafe", for TalkBack. */
private fun readableName(key: String): String =
    key.replace(Regex("([a-z])([A-Z0-9])"), "$1 $2").lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = BColors.Lavender,
    focusedContainerColor = BColors.Lavender,
    unfocusedBorderColor = BColors.Lavender,
    focusedBorderColor = BColors.Violet,
)

private const val DEFAULT_ICON = "Category"
private const val MAX_NAME = 24
