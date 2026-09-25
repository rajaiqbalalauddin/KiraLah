package com.buyless.app.ui.recap

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SouthEast
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyless.app.recap.RecapData
import com.buyless.app.ui.components.appViewModel
import com.buyless.app.ui.components.style
import com.buyless.app.ui.theme.BColors
import com.buyless.app.util.Dates
import com.buyless.app.util.Money
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** One screen of the story. Each has its own colours, like a Wrapped card. */
private data class Slide(val key: String, val bg: Color, val fg: Color, val content: @Composable (RecapData, Color) -> Unit)

private const val SLIDE_MS = 5_500

/**
 * Full-screen story player. Tap the right side to go on, the left side to go back, hold anywhere to
 * pause. Slides advance on their own; the last one waits so the summary can be shared.
 */
@Composable
fun RecapStoryScreen(onClose: () -> Unit) {
    val vm = appViewModel { c, handle -> RecapStoryViewModel(c.transactions, handle) }
    val data by vm.data.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)

    val recap = data
    if (recap == null) {
        Box(Modifier.fillMaxSize().background(BColors.Ink), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BColors.Yellow)
        }
        return
    }
    val slides = remember(recap) { buildSlides(recap) }

    var index by rememberSaveable { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var shownIndex by remember { mutableIntStateOf(-1) }
    val progress = remember { Animatable(0f) }

    // One effect drives the timer: restart on a new slide, stop while held, resume where it left off.
    LaunchedEffect(index, paused) {
        if (shownIndex != index) {
            progress.snapTo(0f)
            shownIndex = index
        }
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * SLIDE_MS).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        if (index < slides.lastIndex) index += 1
    }

    val slide = slides[index.coerceIn(0, slides.lastIndex)]
    Box(
        Modifier
            .fillMaxSize()
            .background(slide.bg)
            .pointerInput(slides.size) {
                detectTapGestures(
                    onPress = {
                        paused = true
                        tryAwaitRelease()
                        paused = false
                    },
                    onTap = { offset ->
                        index = if (offset.x < size.width * 0.3f) (index - 1).coerceAtLeast(0) else (index + 1).coerceAtMost(slides.lastIndex)
                    },
                )
            },
    ) {
        Orbs(slide.fg)

        AnimatedContent(
            targetState = index,
            transitionSpec = { (fadeIn(tween(350)) + scaleIn(initialScale = 0.92f)) togetherWith (fadeOut(tween(200)) + scaleOut(targetScale = 1.04f)) },
            label = "slide",
            modifier = Modifier.fillMaxSize(),
        ) { i ->
            val s = slides[i.coerceIn(0, slides.lastIndex)]
            Box(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 96.dp, bottom = 40.dp)) {
                s.content(recap, s.fg)
            }
        }

        // Progress segments and close button, drawn above the slide.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.indices.forEach { i ->
                    val fill = when {
                        i < index -> 1f
                        i == index -> progress.value
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(slide.fg.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(slide.fg))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(recap.periodLabel, color = slide.fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close recap", tint = slide.fg) }
            }
        }

        if (index == slides.lastIndex) {
            SummaryActions(recap, slide.fg, slide.bg, onReplay = { index = 0 }, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

private fun buildSlides(d: RecapData): List<Slide> = buildList {
    add(Slide("intro", BColors.Violet, Color.White) { r, fg -> IntroSlide(r, fg) })
    add(Slide("total", BColors.Ink, Color.White) { r, fg -> TotalSlide(r, fg) })
    d.changePercent?.let { change ->
        val less = change < 0
        add(Slide("compare", if (less) Color(0xFF10B981) else BColors.Coral, if (less) Color(0xFF063B2B) else Color.White) { r, fg -> CompareSlide(r, fg) })
    }
    if (d.categories.isNotEmpty()) add(Slide("categories", BColors.Yellow, BColors.Ink) { r, fg -> CategorySlide(r, fg) })
    if (d.topMerchants.isNotEmpty()) add(Slide("merchant", BColors.Coral, Color.White) { r, fg -> MerchantSlide(r, fg) })
    if (d.biggest != null) add(Slide("biggest", Color(0xFF2563EB), Color.White) { r, fg -> BiggestSlide(r, fg) })
    if (d.busiestDay != null) add(Slide("rhythm", Color(0xFF0FA3A3), Color.White) { r, fg -> RhythmSlide(r, fg) })
    if (d.apps.isNotEmpty()) add(Slide("apps", Color(0xFFE0457B), Color.White) { r, fg -> AppsSlide(r, fg) })
    add(Slide("personality", BColors.Violet, Color.White) { r, fg -> PersonalitySlide(r, fg) })
    add(Slide("summary", BColors.Ink, Color.White) { r, fg -> SummarySlide(r, fg) })
}

// ---------------- Slides ----------------

@Composable
private fun IntroSlide(d: RecapData, fg: Color) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker(if (d.isYear) "Your year in money" else "Your month in money", fg)
        PopIn { Big(d.periodLabel, fg, 56.sp) }
        Spacer(Modifier.height(16.dp))
        Body("Every ringgit in and out, wrapped up. Tap to go on.", fg)
    }
}

@Composable
private fun TotalSlide(d: RecapData, fg: Color) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker("You spent", fg)
        CountUpMoney(d.spentSen, fg, 52.sp)
        Spacer(Modifier.height(12.dp))
        Body("across ${d.payments} payment${if (d.payments == 1) "" else "s"}.", fg)
        Body("That is about ${Money.format(d.dailyAverageSen)} a day.", fg)
        if (!d.isYear && d.noSpendDays > 0) {
            Spacer(Modifier.height(20.dp))
            Chip("${d.noSpendDays} no-spend day${if (d.noSpendDays == 1) "" else "s"}", fg)
        }
    }
}

@Composable
private fun CompareSlide(d: RecapData, fg: Color) {
    val change = d.changePercent ?: 0
    val less = change < 0
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        PopIn {
            Icon(if (less) Icons.Rounded.SouthEast else Icons.Rounded.NorthEast, contentDescription = null, tint = fg, modifier = Modifier.size(88.dp))
        }
        Big("${kotlin.math.abs(change)}% ${if (less) "less" else "more"}", fg, 52.sp)
        Body("than last ${if (d.isYear) "year" else "month"} (${Money.format(d.previousSpentSen ?: 0)}).", fg)
        Spacer(Modifier.height(16.dp))
        Body(if (less) "Nice. Your wallet noticed." else "It happens. Next ${if (d.isYear) "year" else "month"} is a fresh start.", fg)
    }
}

@Composable
private fun CategorySlide(d: RecapData, fg: Color) {
    val top = d.categories.take(4)
    val max = top.maxOf { it.second }.coerceAtLeast(1)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker("Where it went", fg)
        Big(top.first().first.style().label, fg, 48.sp)
        Spacer(Modifier.height(24.dp))
        top.forEachIndexed { i, (cat, sen) ->
            val s = cat.style()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(fg.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(s.icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(s.label, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("${sen * 100 / d.spentSen.coerceAtLeast(1)}%", color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    GrowBar(sen.toFloat() / max, fg, delayMs = 200 + i * 150)
                }
            }
        }
    }
}

@Composable
private fun MerchantSlide(d: RecapData, fg: Color) {
    val top = d.topMerchants.first()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker("Your number one spot", fg)
        PopIn { Big(top.name, fg, 44.sp, maxLines = 3) }
        Body("${top.visits} visit${if (top.visits == 1) "" else "s"} · ${Money.format(top.spentSen)}", fg)
        if (d.topMerchants.size > 1) {
            Spacer(Modifier.height(28.dp))
            d.topMerchants.drop(1).forEachIndexed { i, m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 2}", color = fg.copy(alpha = 0.7f), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                    Text(m.name, color = fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${m.visits}x", color = fg.copy(alpha = 0.8f), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun BiggestSlide(d: RecapData, fg: Color) {
    val b = d.biggest ?: return
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker("Biggest single payment", fg)
        CountUpMoney(b.amountSen, fg, 48.sp)
        Spacer(Modifier.height(12.dp))
        Body("${b.merchant} on ${Dates.fullDate(b.timestamp)}, via ${b.sourceLabel}.", fg)
    }
}

@Composable
private fun RhythmSlide(d: RecapData, fg: Color) {
    val day = d.busiestDay ?: return
    val labels = if (d.isYear) d.buckets.map { it.first.take(1) } else listOf("M", "T", "W", "T", "F", "S", "S")
    val values = if (d.isYear) d.buckets.map { it.second } else d.weekdays
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker("Your spending rhythm", fg)
        Big("${day.getDisplayName(JTextStyle.FULL, Locale.ENGLISH)}s hit hardest", fg, 40.sp)
        Spacer(Modifier.height(28.dp))
        Bars(values, labels, fg, highlight = if (d.isYear) values.indices.maxByOrNull { values[it] } ?: -1 else day.value - 1)
    }
}

@Composable
private fun AppsSlide(d: RecapData, fg: Color) {
    val top = d.apps.take(4)
    val max = top.maxOf { it.second }.coerceAtLeast(1)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker("Your go-to app", fg)
        PopIn { Big(top.first().first, fg, 48.sp) }
        Spacer(Modifier.height(24.dp))
        top.forEachIndexed { i, (label, sen) ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Row {
                    Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(Money.format(sen), color = fg, fontSize = 15.sp)
                }
                GrowBar(sen.toFloat() / max, fg, delayMs = 200 + i * 150)
            }
        }
    }
}

@Composable
private fun PersonalitySlide(d: RecapData, fg: Color) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker("Your money personality", fg)
        PopIn { Big(d.personality.title, fg, 54.sp, maxLines = 3) }
        Spacer(Modifier.height(16.dp))
        Body(d.personality.line, fg)
    }
}

@Composable
private fun SummarySlide(d: RecapData, fg: Color) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Kicker("${d.periodLabel} wrapped", fg)
        Big(d.personality.title, fg, 34.sp)
        StatRow("Spent", Money.format(d.spentSen), fg)
        StatRow("Money in", Money.format(d.inSen), fg)
        StatRow("Payments", d.payments.toString(), fg)
        d.categories.firstOrNull()?.let { StatRow("Top category", it.first.style().label, fg) }
        d.topMerchants.firstOrNull()?.let { StatRow("Top spot", it.name, fg) }
        d.apps.firstOrNull()?.let { StatRow("Go-to app", it.first, fg) }
    }
}

@Composable
private fun SummaryActions(d: RecapData, fg: Color, bg: Color, onReplay: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    Row(modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onReplay,
            colors = ButtonDefaults.buttonColors(containerColor = fg.copy(alpha = 0.14f), contentColor = fg),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Icon(Icons.Rounded.Replay, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Replay")
        }
        Button(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(d))
                context.startActivity(Intent.createChooser(send, "Share your recap"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Share")
        }
    }
}

private fun shareText(d: RecapData): String = buildString {
    appendLine("My ${d.periodLabel} money recap, by KiraLah")
    appendLine("${d.personality.title}: ${d.personality.line}")
    appendLine("Spent ${Money.format(d.spentSen)} across ${d.payments} payments")
    d.categories.firstOrNull()?.let { appendLine("Top category: ${it.first.style().label}") }
    d.topMerchants.firstOrNull()?.let { appendLine("Top spot: ${it.name} (${it.visits} visits)") }
}

// ---------------- Building blocks ----------------

@Composable
private fun Kicker(text: String, fg: Color) {
    Text(text.uppercase(Locale.ENGLISH), color = fg.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Big(text: String, fg: Color, size: TextUnit, maxLines: Int = 2) {
    Text(
        text,
        color = fg,
        style = TextStyle(fontSize = size, lineHeight = size * 1.05f, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Body(text: String, fg: Color) {
    Text(text, color = fg.copy(alpha = 0.9f), fontSize = 18.sp, lineHeight = 25.sp)
}

@Composable
private fun Chip(text: String, fg: Color) {
    Text(
        text,
        color = fg,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(fg.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun StatRow(label: String, value: String, fg: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = fg.copy(alpha = 0.75f), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = fg, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Bouncy entrance for headline content. */
@Composable
private fun PopIn(content: @Composable () -> Unit) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    Box(Modifier.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
    }) { content() }
}

/** Money that counts up from zero, the signature Wrapped moment. Lands exactly on the real amount. */
@Composable
private fun CountUpMoney(targetSen: Long, fg: Color, size: TextUnit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(targetSen) { anim.animateTo(targetSen.toFloat(), tween(1500, easing = FastOutSlowInEasing)) }
    val shown = if (anim.value >= targetSen.toFloat()) targetSen else anim.value.toLong()
    Big(Money.format(shown), fg, size, maxLines = 1)
}

/** A bar that grows from nothing to its share, staggered by [delayMs]. */
@Composable
private fun GrowBar(fraction: Float, fg: Color, delayMs: Int) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(fraction) { grow.animateTo(fraction.coerceIn(0.02f, 1f), tween(900, delayMillis = delayMs, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(10.dp).clip(CircleShape).background(fg.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(grow.value).clip(CircleShape).background(fg))
    }
}

/** Vertical bars (weekdays or months) rising one after another, with the peak highlighted. */
@Composable
private fun Bars(values: List<Long>, labels: List<String>, fg: Color, highlight: Int) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1)
    BoxWithConstraints(Modifier.fillMaxWidth().height(180.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            values.forEachIndexed { i, v ->
                val grow = remember { Animatable(0f) }
                LaunchedEffect(v) { grow.animateTo(v.toFloat() / max, tween(700, delayMillis = 120 + i * 70, easing = FastOutSlowInEasing)) }
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f * grow.value.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(if (i == highlight) fg else fg.copy(alpha = 0.35f)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(labels.getOrElse(i) { "" }, color = fg, fontSize = 12.sp, fontWeight = if (i == highlight) FontWeight.ExtraBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Slow drifting circles behind every slide, drawn in the draw phase so they cost no recomposition. */
@Composable
private fun Orbs(fg: Color) {
    val t = rememberInfiniteTransition(label = "orbs")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(fg.copy(alpha = 0.08f), radius = size.width * 0.55f, center = Offset(size.width * (0.9f - a * 0.2f), size.height * (0.15f + b * 0.1f)))
        drawCircle(fg.copy(alpha = 0.06f), radius = size.width * 0.35f, center = Offset(size.width * (0.1f + b * 0.2f), size.height * (0.85f - a * 0.1f)))
    }
}
