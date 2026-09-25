package com.buyless.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buyless.app.data.model.AppKind
import com.buyless.app.data.model.Category
import com.buyless.app.data.repo.AppsRepository
import com.buyless.app.ui.theme.BColors

// Reusable building blocks shared by every screen, so the look stays consistent.

/** Gives composables access to the icon cache without threading it through every parameter. */
val LocalAppsRepository = staticCompositionLocalOf<AppsRepository?> { null }

@Immutable
data class CategoryStyle(val label: String, val icon: ImageVector, val bg: Color, val fg: Color)

/** Colour and icon per category. Colours differ in lightness too, not only hue. */
fun Category.style(): CategoryStyle = when (this) {
    Category.FOOD -> CategoryStyle("Food", Icons.Rounded.Restaurant, BColors.CoralSoft, BColors.CoralInk)
    Category.TRANSPORT -> CategoryStyle("Transport", Icons.Rounded.DirectionsCar, BColors.BlueSoft, BColors.BlueInk)
    Category.SHOPPING -> CategoryStyle("Shopping", Icons.Rounded.ShoppingBag, BColors.AmberSoft, BColors.Amber)
    Category.BILLS -> CategoryStyle("Bills", Icons.Rounded.Receipt, BColors.GreenSoft, BColors.Green)
    Category.INCOME -> CategoryStyle("Income", Icons.Rounded.Payments, BColors.GreenSoft, BColors.Green)
    Category.TRANSFER -> CategoryStyle("Transfer", Icons.Rounded.SwapHoriz, BColors.VioletSoft, BColors.Violet)
    Category.OTHER -> CategoryStyle("Other", Icons.Rounded.Category, BColors.Border, BColors.Muted)
}

@Composable
fun CategoryBadge(category: Category, size: Dp = 42.dp) {
    val s = category.style()
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(s.bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(s.icon, contentDescription = null, tint = s.fg, modifier = Modifier.size(size / 2))
    }
}

/**
 * Real app icon when available (instantly recognisable), otherwise a coloured tile with a bank or
 * wallet glyph. Icons come from the shared LruCache, so re-showing a row costs no decoding.
 */
@Composable
fun AppBadge(packageName: String, kind: AppKind, size: Dp = 40.dp) {
    val repo = LocalAppsRepository.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon: ImageBitmap? by produceState(initialValue = repo?.cachedIcon(packageName), packageName) {
        if (value == null && repo != null) value = repo.loadIcon(packageName, px)
    }
    val shape = RoundedCornerShape(size * 0.3f)
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        val (bg, fg) = BColors.TilePalette[(packageName.hashCode() and Int.MAX_VALUE) % BColors.TilePalette.size]
        Box(Modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
            Icon(
                if (kind == AppKind.BANK) Icons.Rounded.AccountBalance else Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

/** White rounded card with the hairline border used across the design. */
@Composable
fun BCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(BColors.White)
            .border(1.dp, BColors.Border, RoundedCornerShape(18.dp)),
    ) { content() }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** One transaction line. Sign + colour + "Not counted" tag, so meaning never depends on colour alone. */
@Composable
fun TransactionRow(item: TxnUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 60.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(item.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = BColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                item.amountText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when (item.tone) {
                    AmountTone.IN -> BColors.Green
                    AmountTone.OUT -> BColors.Ink
                    AmountTone.NEUTRAL -> BColors.Faint
                },
            )
            if (item.isInternal) Pill("Not counted", BColors.VioletSoft, BColors.VioletDark)
        }
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Small round coloured icon holder used by stat chips and banners. */
@Composable
fun IconDot(icon: ImageVector, bg: Color, fg: Color, size: Dp = 32.dp, iconSize: Dp = 18.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(iconSize))
    }
}

/** Friendly empty state, so a blank list explains itself instead of looking broken. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconDot(icon, BColors.VioletSoft, BColors.Violet, size = 56.dp, iconSize = 28.dp)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = BColors.Muted, textAlign = TextAlign.Center)
    }
}

/**
 * Swipe a row to the left to delete it. The red layer behind the row explains what will happen, and
 * the caller shows an Undo snackbar, so a slip of the thumb is never permanent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDelete(onDelete: () -> Unit, surface: Color = BColors.White, content: @Composable () -> Unit) {
    val latest by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                latest()
                true
            } else {
                false
            }
        },
        positionalThreshold = { distance -> distance * 0.4f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(BColors.Danger).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Delete", style = MaterialTheme.typography.titleSmall, color = BColors.White)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = BColors.White)
            }
        },
    ) {
        Box(Modifier.background(surface)) { content() }
    }
}
