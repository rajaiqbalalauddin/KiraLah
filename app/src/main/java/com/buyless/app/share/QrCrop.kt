package com.buyless.app.share

/**
 * Works out the square to cut around a QR code found in a screenshot. Pure maths so it is unit tested.
 *
 * Why a margin: scanners need a blank "quiet zone" round the code, and the detector's box sits tight
 * on the black squares. Why a square: a QR is square, so a square crop can be drawn at any size
 * without stretching.
 */
object QrCrop {

    /** Crop box in image pixels. May be smaller than [side] on one axis at an image edge (see [padded]). */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /** Extra space round the code on every side, as a share of the code's size. 4 modules of 25 is 16%. */
    const val MARGIN = 0.12f

    /**
     * Square box centred on the detected code, grown by [MARGIN], then clamped to the image. Returns
     * null when the detected box is too small to be a real QR (under 48 px) or outside the image.
     */
    fun square(left: Int, top: Int, right: Int, bottom: Int, imageWidth: Int, imageHeight: Int): Box? {
        val w = right - left
        val h = bottom - top
        if (w < 48 || h < 48 || imageWidth <= 0 || imageHeight <= 0) return null
        val side = (maxOf(w, h) * (1 + 2 * MARGIN)).toInt()
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        val box = Box(
            left = (cx - side / 2).coerceAtLeast(0),
            top = (cy - side / 2).coerceAtLeast(0),
            right = (cx - side / 2 + side).coerceAtMost(imageWidth),
            bottom = (cy - side / 2 + side).coerceAtMost(imageHeight),
        )
        return box.takeIf { it.width > 0 && it.height > 0 }
    }

    /** Size of the white square the crop is placed on, so a box clipped at an edge still ends up square. */
    fun padded(box: Box): Int = maxOf(box.width, box.height)

    /**
     * Where to draw an image of [srcW] x [srcH] inside a [side] square without stretching it:
     * scaled to fit and centred. Returns left, top, right, bottom.
     */
    fun fit(srcW: Int, srcH: Int, side: Int): IntArray {
        if (srcW <= 0 || srcH <= 0) return intArrayOf(0, 0, side, side)
        val scale = minOf(side.toFloat() / srcW, side.toFloat() / srcH)
        val w = (srcW * scale).toInt()
        val h = (srcH * scale).toInt()
        val l = (side - w) / 2
        val t = (side - h) / 2
        return intArrayOf(l, t, l + w, t + h)
    }
}
