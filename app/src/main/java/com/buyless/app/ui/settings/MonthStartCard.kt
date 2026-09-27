package com.buyless.app.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.buyless.app.ui.components.IconDot
import com.buyless.app.ui.theme.BColors
import com.buyless.app.util.MonthPeriods
import java.time.LocalDate
import java.time.YearMonth

/**
 * Settings row for the day a "month" starts on, for people who budget from payday to payday.
 * Shows an example range so the effect is clear before and after picking.
 */
@Composable
fun MonthStartCard(day: Int, onPick: (Int) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val example = MonthPeriods.rangeText(MonthPeriods.periodOf(today, day), day, today)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BColors.Surface)
            .border(1.dp, BColors.Border, RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Change month start day") { picking = true }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDot(Icons.Rounded.CalendarMonth, BColors.BlueSoft, BColors.BlueInk, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Month starts on", style = MaterialTheme.typography.titleMedium)
            Text(
                if (day == 1) "The 1st, a normal calendar month" else "The ${ordinal(day)}. This month is $example",
                style = MaterialTheme.typography.bodySmall,
                color = BColors.Muted,
            )
        }
        Text(ordinal(day), style = MaterialTheme.typography.titleMedium, color = BColors.Violet)
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = BColors.Muted)
    }

    if (picking) DayPickerDialog(day, onDismiss = { picking = false }, onPick = { onPick(it); picking = false })
}

/** A 1 to 31 grid, like a calendar page, so any day is one tap away. */
@Composable
private fun DayPickerDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var chosen by rememberSaveable { mutableStateOf(current) }
    val today = LocalDate.now()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Month starts on") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Pick the day your month begins, like payday. Home, Activity, Recap and monthly limits all follow it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BColors.Muted,
                )
                // Plain rows rather than a lazy grid: 31 cells is tiny, and a lazy grid inside a
                // dialog needs a fixed height to measure.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..31).chunked(7).forEach { week ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            week.forEach { d ->
                                val selected = d == chosen
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(if (selected) BColors.Violet else Color.Transparent)
                                        .selectable(selected = selected, role = Role.RadioButton) { chosen = d },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(d.toString(), style = MaterialTheme.typography.titleSmall, color = if (selected) BColors.OnColor else BColors.Ink)
                                }
                            }
                            // Keeps the last row's cells the same size as the others.
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Text(
                    buildString {
                        append("For example: ")
                        append(MonthPeriods.rangeText(MonthPeriods.periodOf(today, chosen), chosen, today))
                        // Say plainly what happens to short months, so a Feb 28 start is not a surprise.
                        if (chosen > 28) {
                            val feb = YearMonth.of(today.year, 2)
                            append(". In shorter months it starts on the last day (${MonthPeriods.startOf(feb, chosen).dayOfMonth} Feb).")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = BColors.Ink,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(chosen) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** 1st, 2nd, 3rd, 4th ... 11th, 12th, 13th ... 21st, 22nd, 23rd ... 31st. */
fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}
