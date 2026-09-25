package com.buyless.app.ui.split

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.buyless.app.container
import com.buyless.app.ui.theme.BColors

/**
 * Full-screen receipt photo. Pinch to zoom, drag to pan, double-tap to zoom in or reset. Useful when
 * a friend asks "what did I order?" long after the meal.
 */
@Composable
internal fun ReceiptViewer(path: String, title: String, onClose: () -> Unit) {
    val repo = LocalContext.current.container.splitHistory
    val image by produceState<ImageBitmap?>(null, path) { value = repo.fullImage(path) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(BColors.Ink)
            // Swallow taps so nothing underneath reacts while the viewer is open.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val bitmap = image
        if (bitmap == null) {
            CircularProgressIndicator(color = BColors.Yellow, modifier = Modifier.align(Alignment.Center))
        } else {
            Image(
                bitmap,
                contentDescription = "Receipt photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        })
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
        Row(
            Modifier.fillMaxWidth().background(BColors.Ink.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = BColors.Yellow, modifier = Modifier.padding(start = 8.dp).size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = BColors.White, style = MaterialTheme.typography.titleMedium)
                Text("Pinch to zoom, double-tap to reset", color = BColors.VioletOnDark, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close receipt", tint = BColors.White) }
        }
    }
}

/** Small rounded receipt preview for history cards, from the repository's thumbnail cache. */
@Composable
internal fun ReceiptThumb(path: String?, modifier: Modifier = Modifier) {
    val repo = LocalContext.current.container.splitHistory
    val thumb by produceState<ImageBitmap?>(null, path) { value = path?.let { repo.thumbnail(it) } }
    Box(modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(BColors.VioletSoft), contentAlignment = Alignment.Center) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = BColors.Violet)
        }
    }
}

