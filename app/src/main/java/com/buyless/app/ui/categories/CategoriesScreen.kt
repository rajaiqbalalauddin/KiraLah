package com.buyless.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.data.model.Category
import com.buyless.app.ui.components.CategoryBadge
import com.buyless.app.ui.components.CategoryStyle
import com.buyless.app.ui.components.SectionHeader
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.components.style
import com.buyless.app.ui.theme.BColors

/** Settings > Categories: every category in one list. Tap one of yours to edit or delete it. */
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> CategoriesViewModel(c.categories) }
    val custom by vm.custom.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "top") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                    Text("Categories", style = MaterialTheme.typography.headlineSmall)
                }
            }

            item(key = "yoursHeader") { SectionHeader("Yours") }
            if (custom.isEmpty()) {
                item(key = "none") {
                    Text(
                        "None yet. Make one for anything the built-in ones miss, like Kucing, Tuition or Zakat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BColors.Muted,
                    )
                }
            }
            items(custom, key = { it.id }) { c ->
                CategoryRow(c.style(), note = null, onClick = { editingId = c.id })
            }

            item(key = "builtInHeader") { SectionHeader("Built in") }
            items(BuiltIns, key = { it.name }) { cat ->
                CategoryRow(cat.style(), note = "Built in", onClick = null)
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editingId = NEW_CATEGORY },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("New category") },
            containerColor = BColors.Ink,
            contentColor = BColors.Surface,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    CategoryEditing(editingId, onDone = { editingId = null })
}

@Composable
private fun CategoryRow(style: CategoryStyle, note: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BColors.Surface)
            .border(1.dp, BColors.Border, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Edit ${style.label}", onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(style, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(style.label, style = MaterialTheme.typography.titleMedium)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = BColors.Muted)
        }
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = BColors.Muted)
    }
}

/** TRANSFER is set by pairing, never picked, so it is not listed. */
private val BuiltIns = listOf(Category.FOOD, Category.TRANSPORT, Category.SHOPPING, Category.BILLS, Category.INCOME, Category.OTHER)
