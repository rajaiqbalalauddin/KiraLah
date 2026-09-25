package com.buyless.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrCropTest {

    @Test fun squareAroundQrInScreenshot() {
        // A MAE "My QR" screenshot 1080x2400 with the code at 240..840 x 900..1500.
        val box = QrCrop.square(240, 900, 840, 1500, 1080, 2400)!!
        assertEquals(744, box.width)  // 600 + 12% margin each side
        assertEquals(744, box.height)
        assertEquals(540, (box.left + box.right) / 2) // stays centred on the code
        assertEquals(1200, (box.top + box.bottom) / 2)
    }

    @Test fun clampedAtEdgeIsPaddedBackToSquare() {
        val box = QrCrop.square(0, 0, 500, 500, 520, 2000)!!
        assertEquals(0, box.left)
        assertEquals(0, box.top)
        assertEquals(520, box.right)
        assertEquals(560, QrCrop.padded(box)) // drawn on a 560 square, so no stretch
    }

    @Test fun tinyDetectionsAreIgnored() {
        assertNull(QrCrop.square(10, 10, 40, 40, 1000, 1000))
    }

    @Test fun fitKeepsProportions() {
        // A tall 500x1000 picture in an 800 square: 400x800, centred.
        val r = QrCrop.fit(500, 1000, 800)
        assertEquals(listOf(200, 0, 600, 800), r.toList())
        assertEquals(listOf(0, 0, 800, 800), QrCrop.fit(300, 300, 800).toList())
    }
}
