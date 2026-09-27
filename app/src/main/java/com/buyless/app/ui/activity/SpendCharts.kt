package com.buyless.app.ui.activity

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.buyless.app.ui.categories.LocalCategoryCatalog
import com.buyless.app.ui.components.CategoryBadge
import com.buyless.app.ui.theme.BColors
import com.buyless.app.util.Money

/**
 * The spending card on Activity: a bar per day of the month, or a bar per month for the last six,
 * switched with a two-way toggle. Tap a bar to read its exact amount on the line above the chart.
 * One series, so no legend: the title and the toggle say what the bars are.
 */
@Composable
fun SpendTrendCard(charts: SpendCharts, modifier: Modifier = Modifier) {
    var byMonth by rememberSaveable { mutableStateOf(false) }
    val bars = if (byMonth) charts.months else charts.days
    // Tapped bar, reset when the view or the data changes so it never points at the wrong bar.
    var picked by remember(bars) { mutableStateOf<Int?>(null) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BColors.Surface)
            .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Spending", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ViewToggle(byMonth, onChange = { byMonth = it })
        }
        // Tooltip line: the tapped bar's exact figure, otherwise the average as a reference point.
        Text(
            picked?.let { bars.getOrNull(it)?.detail } ?: if (byMonth) charts.monthAverageText else charts.dayAverageText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (picked != null) BColors.Ink else BColors.Muted,
            fontWeight = if (picked != null) FontWeight.SemiBold else FontWeight.Normal,
        )
        BarChart(
            bars = bars,
            picked = picked,
            onPick = { picked = if (picked == it) null else it },
            // Days: label every 7th day so 31 labels never collide. Months: label all six.
            labelEvery = if (byMonth) 1 else 7,
            modifier = Modifier.fillMaxWidth().height(150.dp),
        )
    }
}

/** Days | Months. The chosen segment is filled; both are real radio buttons for TalkBack. */
@Composable
private fun ViewToggle(byMonth: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(BColors.Lavender).padding(3.dp)) {
        listOf(false to "Days", true to "Months").forEach { (value, label) ->
            val chosen = value == byMonth
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (chosen) BColors.Ink else BColors.Muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (chosen) BColors.Surface else Color.Transparent)
                    .selectable(selected = chosen, role = Role.RadioButton, onClick = { onChange(value) })
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Plain Canvas bar chart. Bars have 4dp rounded tops and square bottoms on the baseline, with a
 * gap between bars so neighbours never merge. One recessive dashed line marks the largest value,
 * labelled at the left. The tapped bar turns to ink and the current day or month is labelled in
 * bold, so neither relies on colour alone. Bars grow in once when the data changes.
 */
@Composable
private fun BarChart(bars: List<ChartBar>, picked: Int?, onPick: (Int) -> Unit, labelEvery: Int, modifier: Modifier) {
    val max = bars.maxOfOrNull { it.sen }?.coerceAtLeast(1) ?: 1
    val grow = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(450))
    }
    val barColor = BColors.Violet
    val pickedColor = BColors.Ink
    val gridColor = BColors.Border
    val baseColor = BColors.Border
    val peak = bars.withIndex().maxByOrNull { it.value.sen }?.takeIf { it.value.sen > 0 }
    val summary = peak?.let { "Spending chart, ${bars.size} bars. Highest: ${it.value.detail}" } ?: "Spending chart, no spending yet"

    Column(modifier.semantics { contentDescription = summary }) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    // Tap only, no drag: a sideways drag here must still be free to change tab.
                    .pointerInput(bars) {
                        detectTapGestures { pos ->
                            val slot = size.width / bars.size.toFloat()
                            val i = (pos.x / slot).toInt().coerceIn(0, bars.lastIndex)
                            onPick(i)
                        }
                    },
            ) {
                val slot = size.width / bars.size
                val gap = 2.dp.toPx().coerceAtLeast(slot * 0.25f)
                val barWidth = (slot - gap).coerceAtLeast(1f)
                val radius = minOf(4.dp.toPx(), barWidth / 2)
                val top = 18.dp.toPx() // room for the peak label above the gridline
                val chartHeight = size.height - top

                // Peak gridline, dashed and recessive.
                drawLine(gridColor, Offset(0f, top), Offset(size.width, top), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))

                bars.forEachIndexed { i, bar ->
                    if (bar.sen <= 0) return@forEachIndexed
                    val h = (bar.sen.toFloat() / max * chartHeight * grow.value).coerceAtLeast(2.dp.toPx())
                    val left = i * slot + gap / 2
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left, top = size.height - h, right = left + barWidth, bottom = size.height,
                                topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                                bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                            ),
                        )
                    }
                    drawPath(path, if (i == picked) pickedColor else barColor)
                }
                // Baseline.
                drawRect(baseColor, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
            }
            Text(
                Money.format(max),
                style = MaterialTheme.typography.labelMedium,
                color = BColors.Muted,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        Spacer(Modifier.height(4.dp))
        // X labels drawn on a Canvas so a label can be wider than its bar slot (31 days in a phone
        // width leaves ~10dp per day). Regular labels next to the bold current one are skipped so
        // they never overlap.
        val measurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.labelMedium
        val muted = BColors.Muted
        val ink = BColors.Ink
        val current = bars.indexOfFirst { it.isCurrent }
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val slot = size.width / bars.size
            bars.forEachIndexed { i, bar ->
                val isCurrent = i == current
                val regular = i % labelEvery == 0 && (labelEvery == 1 || current < 0 || kotlin.math.abs(i - current) >= 3)
                if (!isCurrent && !regular) return@forEachIndexed
                val layout = measurer.measure(
                    bar.label,
                    labelStyle.copy(color = if (isCurrent) ink else muted, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal),
                )
                val x = (i * slot + slot / 2 - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                drawText(layout, topLeft = Offset(x, 0f))
            }
        }
    }
}

/**
 * "Where it went": the month's top categories as bars on a shared scale. The badge carries the
 * category's identity; the bar is one colour, because its length is the only thing it encodes.
 */
@Composable
fun CategoryBreakdownCard(shares: List<CategoryShare>, modifier: Modifier = Modifier) {
    val catalog = LocalCategoryCatalog.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BColors.Surface)
            .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Where it went", style = MaterialTheme.typography.titleLarge)
        shares.forEach { share ->
            val style = catalog.style(share.key)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(style, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(style.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${share.amountText} · ${share.percent}%", style = MaterialTheme.typography.titleSmall, color = BColors.Muted)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(BColors.Lavender)) {
                        Box(Modifier.fillMaxWidth(share.fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(BColors.Violet))
                    }
                }
            }
        }
    }
}
