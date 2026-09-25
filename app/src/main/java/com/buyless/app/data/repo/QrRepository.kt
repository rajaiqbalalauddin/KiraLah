package com.buyless.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.buyless.app.data.db.PaymentQrDao
import com.buyless.app.data.db.PaymentQrEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Saved payment QRs (MAE, TNG, DuitNow...). Picked images are copied into private app storage, so
 * the QR keeps working even if the user deletes the original screenshot.
 *
 * Images are downscaled on import (QR codes scan fine at ~1000 px) and decoded bitmaps are cached,
 * so flipping between QRs while showing friends is instant and memory stays small.
 */
class QrRepository(private val context: Context, private val dao: PaymentQrDao) {

    private val dir: File get() = File(context.filesDir, "qr").apply { mkdirs() }
    private val cache = LruCache<String, ImageBitmap>(6)

    fun observeAll(): Flow<List<PaymentQrEntity>> = dao.observeAll().distinctUntilChanged()

    /** Copies and shrinks the picked image, then saves it. Returns the new id, or null if unreadable. */
    suspend fun add(uri: Uri, label: String): Long? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(uri, MAX_SIDE_PX) ?: return@withContext null
        val file = File(dir, "${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } // lossless keeps QR edges sharp
        bitmap.recycle()
        dao.insert(PaymentQrEntity(label = label.ifBlank { "My QR" }, filePath = file.absolutePath, addedAt = System.currentTimeMillis()))
    }

    suspend fun delete(qr: PaymentQrEntity) = withContext(Dispatchers.IO) {
        cache.remove(qr.filePath)
        File(qr.filePath).delete()
        dao.delete(qr.id)
    }

    suspend fun load(path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        val image = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        if (image != null) cache.put(path, image)
        return image
    }

    /** Two-pass decode: read the size first, then decode at a power-of-two reduction. */
    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) } // only fills bounds, returns null by design
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private companion object {
        const val MAX_SIDE_PX = 1200
    }
}
