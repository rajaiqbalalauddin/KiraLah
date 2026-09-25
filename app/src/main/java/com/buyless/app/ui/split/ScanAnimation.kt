package com.buyless.app.ui.split

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buyless.app.split.ScanStage
import com.buyless.app.ui.theme.BColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loading screen while Gemini reads the receipt. The photo sits on a paper card, a warm beam sweeps
 * over it, and item "rows" shimmer in underneath, so the wait feels like the app is visibly reading
 * the bill. Every animation runs on one infinite transition and draws in the draw phase, so it costs
 * no recomposition per frame.
 */
@Composable
internal fun ScanningView(stage: ScanStage, photo: Uri?, onCancel: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, photo) { value = photo?.let { loadThumbnail(context, it, 720) } }

    val t = rememberInfiniteTransition(label = "scan")
    val beam by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beam")
    val shimmer by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmer")
    val bracket by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bracket")
    val float by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")

    Column(
        Modifier.fillMaxSize().background(BColors.Ink).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Reading your receipt", style = MaterialTheme.typography.headlineSmall, color = BColors.White)

        // The receipt card, gently floating.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(0.78f)
                .graphicsLayer {
                    translationY = float * 6.dp.toPx()
                    rotationZ = float * 0.8f
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(BColors.White),
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(BColors.Lavender)) {
                    val image = thumb
                    if (image != null) {
                        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = BColors.VioletSoft, modifier = Modifier.size(96.dp).align(Alignment.Center))
                    }
                    // Dim the photo slightly so the beam reads clearly.
                    Box(Modifier.fillMaxSize().background(BColors.Ink.copy(alpha = 0.18f)))

                    // Glow beam: a soft band plus a bright line, moved by the draw phase only.
                    val h = maxHeight
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .graphicsLayer { translationY = beam * (h - 90.dp).toPx() }
                            .drawBehind {
                                drawRect(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, BColors.Yellow.copy(alpha = 0.35f), Color.Transparent),
                                    ),
                                )
                                drawLine(
                                    BColors.Yellow,
                                    Offset(size.width * 0.04f, size.height / 2),
                                    Offset(size.width * 0.96f, size.height / 2),
                                    strokeWidth = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            },
                    )

                    // Pulsing corner brackets, like a camera locking on.
                    Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                        val len = 26.dp.toPx() + bracket * 6.dp.toPx()
                        val w = 4.dp.toPx()
                        val c = BColors.Yellow
                        val (x1, y1, x2, y2) = listOf(0f, 0f, size.width, size.height)
                        drawLine(c, Offset(x1, y1), Offset(x1 + len, y1), w, StrokeCap.Round)
                        drawLine(c, Offset(x1, y1), Offset(x1, y1 + len), w, StrokeCap.Round)
                        drawLine(c, Offset(x2, y1), Offset(x2 - len, y1), w, StrokeCap.Round)
                        drawLine(c, Offset(x2, y1), Offset(x2, y1 + len), w, StrokeCap.Round)
                        drawLine(c, Offset(x1, y2), Offset(x1 + len, y2), w, StrokeCap.Round)
                        drawLine(c, Offset(x1, y2), Offset(x1, y2 - len), w, StrokeCap.Round)
                        drawLine(c, Offset(x2, y2), Offset(x2 - len, y2), w, StrokeCap.Round)
                        drawLine(c, Offset(x2, y2), Offset(x2, y2 - len), w, StrokeCap.Round)
                    }
                }

                // Item rows shimmering in, hinting at the list being pulled out of the photo.
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(0.62f, 0.48f, 0.56f).forEach { nameWidth ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            SkeletonBar(shimmer, Modifier.fillMaxWidth(nameWidth))
                            SkeletonBar(shimmer, Modifier.width(52.dp))
                        }
                    }
                }
            }
        }

        StageList(stage)

        TextButton(onClick = onCancel) { Text("Cancel", color = BColors.VioletOnDark) }
    }
}

/** A rounded placeholder bar with a light sweep passing across it. */
@Composable
private fun SkeletonBar(shimmer: Float, modifier: Modifier) {
    Canvas(modifier.height(12.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(BColors.Border, size = size, cornerRadius = r)
        val x = shimmer * size.width
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, BColors.VioletSoft, Color.Transparent),
                startX = x - size.width * 0.4f,
                endX = x + size.width * 0.4f,
            ),
            size = size,
            cornerRadius = r,
        )
    }
}

/** Three steps with done / active / waiting states. The active line is announced to screen readers. */
@Composable
private fun StageList(stage: ScanStage) {
    val steps = if (stage == ScanStage.ON_DEVICE) {
        listOf("Reading on this phone" to ScanStage.ON_DEVICE)
    } else {
        listOf(
            "Preparing the photo" to ScanStage.PREPARING,
            "Reading every item with Gemini" to ScanStage.READING,
            "Double-checking the totals" to ScanStage.DOUBLE_CHECKING,
        )
    }
    val activeIndex = steps.indexOfFirst { it.second == stage }.coerceAtLeast(0)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, (label, _) ->
            val state = when {
                i < activeIndex -> 2
                i == activeIndex -> 1
                else -> 0
            }
            // The double-check step only appears once it actually starts.
            if (steps.size == 3 && i == 2 && state == 0) return@forEachIndexed
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepDot(state)
                AnimatedContent(
                    targetState = label to state,
                    transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                    label = "step$i",
                ) { (text, s) ->
                    Text(
                        text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (s == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (s == 0) BColors.Faint else BColors.White,
                        textAlign = TextAlign.Start,
                        modifier = if (s == 1) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
                    )
                }
            }
        }
    }
}

/** 2 = done (green tick), 1 = working (spinning arc), 0 = waiting (dim dot). */
@Composable
private fun StepDot(state: Int) {
    when (state) {
        2 -> Box(Modifier.size(24.dp).clip(CircleShape).background(BColors.Green), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = BColors.White, modifier = Modifier.size(14.dp))
        }
        1 -> {
            val spin = rememberInfiniteTransition(label = "spin")
            val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")
            Canvas(Modifier.size(24.dp)) {
                drawCircle(BColors.VioletDark, style = Stroke(3.dp.toPx()))
                drawArc(
                    BColors.Yellow,
                    startAngle = angle,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        else -> Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(BColors.Faint))
        }
    }
}

/** Small, upright preview of the photo for the animation. Decoded off the main thread. */
private suspend fun loadThumbnail(context: Context, uri: Uri, maxSide: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext null
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val upright: Bitmap = if (rotation == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
        upright.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
