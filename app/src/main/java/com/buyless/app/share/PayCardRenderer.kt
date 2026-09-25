package com.buyless.app.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils

/**
 * Draws the picture that goes into WhatsApp: a bright card with the amount and the payment QR.
 * One image carries both, so a friend can screenshot it or scan it straight from the chat.
 *
 * Plain android.graphics (not Compose) because it runs off screen on a background thread and only
 * needs a handful of shapes and text. Output is 1080 px wide, sharp on any phone without being heavy.
 */
object PayCardRenderer {

    private const val W = 1080
    private const val PAD = 72f
    private const val INK = 0xFF16132B.toInt()
    private const val MUTED = 0xFF5E5A78.toInt()
    private const val LAVENDER = 0xFFF5F3FF.toInt()
    private const val GREEN = 0xFF0B7A5F.toInt()
    private const val GREEN_SOFT = 0xFFD8F5EA.toInt()
    private const val VIOLET_ON_DARK = 0xFFD9D1FF.toInt()

    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

    /** Everything the per-person card shows. Colours are ARGB ints from the person's avatar colour. */
    data class PersonCard(
        val name: String,
        val amountText: String,
        val placeText: String,
        val qr: Bitmap?,
        val qrLabel: String?,
        val bg: Int,
        val fg: Int,
    )

    data class GroupRow(val name: String, val amountText: String, val paid: Boolean, val bg: Int, val fg: Int)

    fun person(card: PersonCard): Bitmap {
        val qrSize = 760
        val height = if (card.qr != null) 1720 else 1040
        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(card.bg)

        var y = PAD + 40f
        text(c, "BILL SPLIT", PAD, y, 34f, card.fg, alpha = 170, typeface = bold, spacing = 0.12f)
        y += 110f
        text(c, "Hi ${card.name.trim().substringBefore(' ')},", PAD, y, 64f, card.fg, typeface = bold)
        y += 70f
        text(c, "your share ${card.placeText}", PAD, y, 42f, card.fg, alpha = 210)
        y += 190f
        text(c, card.amountText, PAD, y, 150f, card.fg, typeface = bold)
        y += 80f

        // White panel with the QR, like the card shown across the table.
        val panelTop = y
        val panelBottom = height - PAD
        val panel = RectF(PAD, panelTop, W - PAD, panelBottom)
        c.drawRoundRect(panel, 56f, 56f, fill(Color.WHITE))
        if (card.qr != null) {
            val left = (W - qrSize) / 2
            val top = (panelTop + 60f).toInt()
            drawQr(c, card.qr, left, top, qrSize)
            val label = card.qrLabel?.let { "Scan to pay · $it" } ?: "Scan to pay"
            centred(c, label, panelTop + 60f + qrSize + 80f, 44f, INK, bold)
        } else {
            centred(c, "Transfer when you can", panelTop + 140f, 48f, INK, bold)
            centred(c, "Thank you!", panelTop + 210f, 40f, MUTED, null)
        }
        return bmp
    }

    /** One picture for the group chat: everyone's amount, who has paid, and the QR at the bottom. */
    fun group(title: String, placeText: String, rows: List<GroupRow>, qr: Bitmap?, qrLabel: String?): Bitmap {
        val rowH = 124f
        val qrSize = 620
        val listTop = 360f
        val listBottom = listTop + 40f + rows.size * rowH
        val qrBlock = if (qr != null) qrSize + 180f else 0f
        val height = (listBottom + 40f + qrBlock + PAD).toInt()
        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(INK)

        text(c, "BILL SPLIT", PAD, PAD + 40f, 34f, VIOLET_ON_DARK, typeface = bold, spacing = 0.12f)
        text(c, title, PAD, PAD + 150f, 72f, Color.WHITE, typeface = bold)
        text(c, placeText, PAD, PAD + 220f, 40f, VIOLET_ON_DARK)

        c.drawRoundRect(RectF(PAD, listTop, W - PAD, height - PAD), 56f, 56f, fill(Color.WHITE))
        var y = listTop + 40f
        for (row in rows) {
            val cy = y + rowH / 2
            c.drawCircle(PAD + 80f, cy, 40f, fill(row.bg))
            centredAt(c, initials(row.name), PAD + 80f, cy + 15f, 38f, row.fg, bold)
            val amountWidth = paint(44f, INK, bold).measureText(row.amountText)
            val tagWidth = if (row.paid) 150f else 0f
            val nameMax = (W - PAD - 48f) - (PAD + 150f) - amountWidth - tagWidth - 40f
            text(c, row.name, PAD + 150f, cy + 15f, 44f, INK, maxWidth = nameMax)
            val right = W - PAD - 48f
            text(c, row.amountText, right - amountWidth, cy + 15f, 44f, INK, typeface = bold)
            if (row.paid) {
                val tag = RectF(right - amountWidth - 150f, cy - 28f, right - amountWidth - 24f, cy + 28f)
                c.drawRoundRect(tag, 28f, 28f, fill(GREEN_SOFT))
                centredAt(c, "Paid", tag.centerX(), cy + 11f, 30f, GREEN, bold)
            }
            y += rowH
        }
        if (qr != null) {
            c.drawLine(PAD + 48f, listBottom, W - PAD - 48f, listBottom, fill(LAVENDER).apply { strokeWidth = 4f })
            val left = (W - qrSize) / 2
            val top = (listBottom + 40f).toInt()
            drawQr(c, qr, left, top, qrSize)
            centred(c, qrLabel?.let { "Scan to pay · $it" } ?: "Scan to pay", top + qrSize + 80f, 44f, INK, bold)
        }
        return bmp
    }

    // ---- small drawing helpers ----

    /**
     * Draws the QR inside a square slot, scaled to fit and centred, never stretched. Saved QRs are
     * already cropped square, but an odd-shaped image still keeps its proportions.
     * Nearest-neighbour scaling keeps the QR squares crisp so it scans from a phone screen.
     */
    private fun drawQr(c: Canvas, qr: Bitmap, left: Int, top: Int, side: Int) {
        val d = QrCrop.fit(qr.width, qr.height, side)
        c.drawBitmap(qr, null, Rect(left + d[0], top + d[1], left + d[2], top + d[3]), Paint().apply { isFilterBitmap = false })
    }

    private fun initials(name: String) =
        name.trim().split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun paint(size: Float, color: Int, typeface: Typeface?, alpha: Int = 255, spacing: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            this.alpha = alpha
            if (typeface != null) this.typeface = typeface
            letterSpacing = spacing
        }

    /** Draws one line, cutting it with "..." when it would run past [maxWidth]. */
    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        alpha: Int = 255, typeface: Typeface? = null, spacing: Float = 0f, maxWidth: Float = W - 2 * PAD,
    ) {
        val p = paint(size, color, typeface, alpha, spacing)
        val fitted = TextUtils.ellipsize(s, p, maxWidth, TextUtils.TruncateAt.END).toString()
        c.drawText(fitted, x, y, p)
    }

    private fun centred(c: Canvas, s: String, y: Float, size: Float, color: Int, typeface: Typeface?) =
        centredAt(c, s, W / 2f, y, size, color, typeface)

    private fun centredAt(c: Canvas, s: String, cx: Float, y: Float, size: Float, color: Int, typeface: Typeface?) {
        val p = paint(size, color, typeface).apply { textAlign = Paint.Align.CENTER }
        c.drawText(s, cx, y, p)
    }
}
