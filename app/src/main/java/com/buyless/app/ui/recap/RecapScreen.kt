package com.buyless.app.ui.recap

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.ui.components.EmptyState
import com.buyless.app.ui.components.Pill
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.theme.BColors

/** Bold card colours for the archive, one per month so the grid reads like a shelf of album covers. */
internal val RecapColors = listOf(
    Color(0xFF5B3DF5) to Color.White,
    Color(0xFFFF6B3D) to Color.White,
    Color(0xFF0FA3A3) to Color.White,
    Color(0xFFFFC940) to Color(0xFF16132B),
    Color(0xFF2563EB) to Color.White,
    Color(0xFFE0457B) to Color.White,
    Color(0xFF10B981) to Color(0xFF063B2B),
)

/** The Recap tab: this year's banner, then every month with data, newest first. */
@Composable
fun RecapScreen(onOpen: (String) -> Unit) {
    val vm = appViewModel { c, _ -> RecapArchiveViewModel(c.transactions) }
    val state by vm.state.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Recap", style = MaterialTheme.typography.headlineSmall)
                Text("Your money, wrapped. Tap a card to play its story.", style = MaterialTheme.typography.bodyMedium, color = BColors.Muted)
            }
        }

        if (!state.loading && state.months.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Rounded.AutoAwesome, "Nothing to recap yet", "Once payments are recorded, each month gets its own story here.")
            }
        }

        state.years.forEach { year ->
            item(key = "y${year.year}", span = { GridItemSpan(maxLineSpan) }) { YearBanner(year) { onOpen(year.period) } }
        }

        items(state.months, key = { it.period }) { card -> MonthTile(card) { onOpen(card.period) } }
    }
}

/** Dark banner with slowly drifting colour orbs, like a year-end wrapped cover. */
@Composable
private fun YearBanner(year: YearCard, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orbs")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.9f)
            .clip(RoundedCornerShape(26.dp))
            .background(BColors.Ink)
            .clickable(onClickLabel = "Play your ${year.year} recap", onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(BColors.Violet, radius = size.height * 0.55f, center = Offset(size.width * (0.85f - drift * 0.1f), size.height * 0.15f))
            drawCircle(BColors.Coral, radius = size.height * 0.32f, center = Offset(size.width * (0.62f + drift * 0.08f), size.height * 1.0f))
            drawCircle(BColors.Yellow, radius = size.height * 0.16f, center = Offset(size.width * 0.95f, size.height * (0.8f - drift * 0.2f)))
        }
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(if (year.inProgress) "So far" else "Full year", BColors.White.copy(alpha = 0.16f), BColors.White)
            }
            Column {
                Text("Your ${year.year}", color = BColors.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Text("${year.totalText} across ${year.months} month${if (year.months == 1) "" else "s"}", color = BColors.VioletOnDark, style = MaterialTheme.typography.bodyMedium)
            }
        }
        PlayDot(Modifier.align(Alignment.BottomEnd).padding(18.dp), BColors.White, BColors.Ink)
    }
}

/** A month as a square "cover": big month name, total, and whether it is still in progress. */
@Composable
private fun MonthTile(card: MonthCard, onClick: () -> Unit) {
    val (bg, fg) = RecapColors[card.colorIndex % RecapColors.size]
    Box(
        Modifier
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(onClickLabel = "Play ${card.month} recap", onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(fg.copy(alpha = 0.12f), radius = size.width * 0.55f, center = Offset(size.width * 1.05f, size.height * 0.05f))
            drawCircle(fg.copy(alpha = 0.08f), radius = size.width * 0.3f, center = Offset(size.width * 0.1f, size.height * 1.05f))
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(card.year.toString(), color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                Text(card.month, color = fg, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp)
            }
            Column {
                if (card.inProgress) Pill("In progress", fg.copy(alpha = 0.18f), fg)
                Spacer(Modifier.size(6.dp))
                Text(card.totalText, color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(card.countText, color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
            }
        }
        PlayDot(Modifier.align(Alignment.TopEnd).padding(12.dp), fg, bg)
    }
}

@Composable
private fun PlayDot(modifier: Modifier, bg: Color, fg: Color) {
    Box(modifier.size(36.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = fg)
    }
}

