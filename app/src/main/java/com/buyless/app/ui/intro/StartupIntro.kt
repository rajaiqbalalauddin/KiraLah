package com.buyless.app.ui.intro

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Colours are fixed on purpose: the intro must match the launcher icon and the system splash,
// which do not follow the app's night mode.
private val Purple = Color(0xFF5B2FD6)
private val Lime = Color(0xFFD4F26A)
private val Paper = Color.White

/** One confetti piece in the "kaching" burst. Precomputed so every frame only does arithmetic. */
private class Piece(val angle: Float, val speed: Float, val size: Float, val color: Color, val isNote: Boolean, val spin: Float)

private val pieces: List<Piece> = List(14) { i ->
    // Evenly spread with a small, fixed jitter, so the burst looks random but is the same every launch.
    val jitter = ((i * 37) % 11 - 5) * 0.035f
    Piece(
        angle = (i / 14f) * 2f * PI.toFloat() + jitter,
        speed = 150f + (i * 53 % 7) * 22f,
        size = 5f + (i * 29 % 5),
        color = if (i % 3 == 0) Lime else Paper,
        isNote = i % 4 == 1,
        spin = if (i % 2 == 0) 260f else -300f,
    )
}

/**
 * Cold-start intro. It picks up exactly where the system splash leaves off (same purple, same
 * banknote, same size), then plays: wind-up squash, springy pop with a tilt wobble, the minus
 * stretching into place, a coin-and-note burst with a shockwave ring, the note lifting while
 * "KiraLah" bounces in letter by letter, and finally a circle that opens to reveal the app.
 *
 * Why it is drawn with Canvas instead of the vector icon: each part (note, minus, confetti) needs
 * its own squash, stretch and timing. Tap anywhere to skip. Skipped entirely when the phone has
 * animations turned off, so it never slows down someone who asked for less motion.
 */
@Composable
fun StartupIntro(onFinished: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    var finished by remember { mutableStateOf(false) }
    // Guarded so a tap-to-skip and the normal ending can never call onFinished twice.
    val finish = {
        if (!finished) {
            finished = true
            onFinished()
        }
    }
    if (animationsOff) {
        LaunchedEffect(Unit) { finish() }
        return
    }

    val squashX = remember { Animatable(1f) }
    val squashY = remember { Animatable(1f) }
    val tilt = remember { Animatable(0f) }
    val minus = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val letters = remember { List(7) { Animatable(0f) } }
    val reveal = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(60)
        // 1. Anticipation: squash down and out, like a ball about to jump.
        launch { squashX.animateTo(1.12f, tween(140, easing = FastOutSlowInEasing)) }
        squashY.animateTo(0.84f, tween(140, easing = FastOutSlowInEasing))

        // 2. Pop: release with a low-damping spring, so it overshoots tall and wobbles back.
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        launch { squashX.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f), initialVelocity = -4f) }
        launch { squashY.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 420f), initialVelocity = 6f) }
        launch { tilt.snapTo(-10f); tilt.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 300f)) }
        launch { minus.snapTo(0.15f); minus.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 520f)) }
        launch { burst.animateTo(1f, tween(800, easing = LinearOutSlowInEasing)) }
        delay(420)

        // 3. The note lifts and shrinks while the name bounces in one letter at a time.
        launch { lift.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 260f)) }
        delay(110)
        letters.forEachIndexed { i, a ->
            launch {
                delay(i * 45L)
                a.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f))
            }
        }
        delay(letters.size * 45L + 140)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        delay(450)

        // 4. Exit: a hole opens from the centre and reveals the app underneath.
        reveal.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        finish()
    }

    Box(
        Modifier
            .fillMaxSize()
            // Offscreen layer so BlendMode.Clear punches a real hole down to the app below.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (reveal.value > 0f) {
                    val maxR = hypot(size.width, size.height) / 2f
                    drawCircle(Color.Black, radius = maxR * reveal.value, blendMode = BlendMode.Clear)
                }
            }
            .background(Purple)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch {
                    reveal.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                    finish()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val out = reveal.value
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1f + 0.12f * out
                    scaleY = 1f + 0.12f * out
                    alpha = 1f - out
                },
        ) {
            // The system splash draws the 108-unit icon at 288dp, so one unit is 288/108 dp.
            val unit = 288.dp.toPx() / 108f
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawBurst(centre, burst.value, unit)
            val liftY = -70.dp.toPx() * lift.value
            val liftScale = 1f - 0.4f * lift.value
            translate(centre.x, centre.y + liftY) {
                rotate(tilt.value, pivot = Offset.Zero) {
                    // Pivot at the note's bottom edge, so the squash looks like it sits on a floor.
                    scale(squashX.value * liftScale, squashY.value * liftScale, pivot = Offset(0f, 15f * unit)) {
                        drawNote(unit, minus.value)
                    }
                }
            }
        }

        Row(
            Modifier
                .offset(y = 58.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.12f * out
                    scaleY = 1f + 0.12f * out
                    alpha = 1f - out
                },
        ) {
            "KiraLah".forEachIndexed { i, ch ->
                val p = letters[i].value
                Text(
                    text = ch.toString(),
                    color = if (i < 4) Paper else Lime,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.graphicsLayer {
                        translationY = (1f - p) * with(density) { 36.dp.toPx() }
                        // Stretch tall while flying up, squash wide as it lands (p overshoots past 1).
                        scaleY = 1f + (1f - p) * 0.35f
                        scaleX = 1f - (1f - p) * 0.2f
                        alpha = p.coerceIn(0f, 1f)
                    },
                )
            }
        }
    }
}

/** The banknote, centred on the origin, in icon units. The seal and dots are purple "holes". */
private fun DrawScope.drawNote(unit: Float, minusStretch: Float) {
    drawRoundRect(
        color = Paper,
        topLeft = Offset(-26.5f * unit, -15f * unit),
        size = Size(53f * unit, 30f * unit),
        cornerRadius = CornerRadius(6f * unit),
    )
    drawCircle(Purple, radius = 9f * unit, center = Offset.Zero)
    drawCircle(Purple, radius = 2.625f * unit, center = Offset(-17.25f * unit, -6.75f * unit))
    drawCircle(Purple, radius = 2.625f * unit, center = Offset(17.25f * unit, 6.75f * unit))
    // Stretch wide and thin, then settle: squash-and-stretch keeps the minus feeling elastic.
    val w = 10.5f * unit * minusStretch
    val h = 4.5f * unit * (2f - minusStretch).coerceIn(0.6f, 1.8f)
    drawRoundRect(
        color = Paper,
        topLeft = Offset(-w / 2f, -h / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2f),
    )
}

/** Shockwave ring plus coins and mini notes flying out with a little gravity. */
private fun DrawScope.drawBurst(centre: Offset, t: Float, unit: Float) {
    if (t <= 0f || t >= 1f) return
    val fade = 1f - t
    val ringR = 30f * unit + 70.dp.toPx() * t
    drawCircle(
        color = Paper.copy(alpha = 0.45f * fade),
        radius = ringR,
        center = centre,
        style = Stroke(width = 10.dp.toPx() * fade),
    )
    val travel = 1f - (1f - t) * (1f - t) // ease-out distance
    val gravity = 120.dp.toPx() * t * t
    for (p in pieces) {
        val d = p.speed.dp.toPx() * travel
        val pos = Offset(centre.x + cos(p.angle) * d, centre.y + sin(p.angle) * d + gravity)
        val s = p.size.dp.toPx()
        val c = p.color.copy(alpha = fade)
        if (p.isNote) {
            rotate(p.spin * t, pivot = pos) {
                drawRoundRect(c, Offset(pos.x - s, pos.y - s * 0.6f), Size(s * 2f, s * 1.2f), CornerRadius(s * 0.3f))
            }
        } else {
            drawCircle(c, radius = s * 0.8f, center = pos)
        }
    }
}
