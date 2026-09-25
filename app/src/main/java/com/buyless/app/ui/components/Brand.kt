package com.buyless.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.buyless.app.R
import com.buyless.app.ui.theme.BColors

/**
 * The "KiraLah" wordmark with "Lah" in brand purple. Kept as one composable so the name is styled
 * the same everywhere and a rebrand is a one-file change.
 */
@Composable
fun KiraLahWordmark(style: TextStyle, modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        append("Kira")
        withStyle(SpanStyle(color = BColors.Brand)) { append("Lah") }
    }
    Text(text, style = style, color = LocalContentColor.current, modifier = modifier)
}

/**
 * The app icon drawn inside the UI (Setup hero). Reuses the launcher foreground drawable so the
 * in-app mark can never drift from the real icon. The drawable is 108dp with its art in the
 * middle 72dp, so it is drawn 1.5x larger and the tile clips the empty margin.
 */
@Composable
fun KiraLahMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(BColors.Brand),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "KiraLah",
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}
