package com.buyless.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.buyless.app.data.db.PaymentQrDao
import com.buyless.app.data.db.PaymentQrEntity
import com.buyless.app.share.QrCrop
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
 *
 * Screenshots of a bank's "My QR" page carry a lot around the code (headers, names, buttons). ML Kit's
 * barcode detector finds the code on the phone, and only a square around it is kept, so the QR fills
 * the pay card and is never stretched.
 */
class QrRepository(private val context: Context, private val dao: PaymentQrDao) {

    private val dir: File get() = File(context.filesDir, "qr").apply { mkdirs() }
    private val cache = LruCache<String, ImageBitmap>(6)

    /** QR-only detector: skipping other barcode types makes it faster and avoids false hits on receipts. */
    private val detector by lazy {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }

    fun observeAll(): Flow<List<PaymentQrEntity>> = dao.observeAll().distinctUntilChanged()

    /** Copies and shrinks the picked image, then saves it. Returns the new id, or null if unreadable. */
    suspend fun add(uri: Uri, label: String): Long? = withContext(Dispatchers.IO) {
        val decoded = decodeScaled(uri, MAX_SIDE_PX) ?: return@withContext null
        val bitmap = cropToQr(decoded)
        val file = File(dir, "${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } // lossless keeps QR edges sharp
        bitmap.recycle()
        dao.insert(PaymentQrEntity(label = label.ifBlank { "My QR" }, filePath = file.absolutePath, addedAt = System.currentTimeMillis()))
    }

    /**
     * Cuts a square around the biggest QR in the picture, on a white background. When no QR is found
     * the whole picture is placed on a white square instead, so it still never stretches.
     */
    private suspend fun cropToQr(source: Bitmap): Bitmap {
        val codes = try {
            detector.process(InputImage.fromBitmap(source, 0)).await()
        } catch (e: Exception) {
            emptyList()
        }
        val box = codes.mapNotNull { it.boundingBox }.maxByOrNull { it.width() * it.height() }
            ?.let { QrCrop.square(it.left, it.top, it.right, it.bottom, source.width, source.height) }

        val (srcRect, side) = if (box != null) {
            Rect(box.left, box.top, box.right, box.bottom) to QrCrop.padded(box)
        } else {
            Rect(0, 0, source.width, source.height) to maxOf(source.width, source.height)
        }
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val d = QrCrop.fit(srcRect.width(), srcRect.height(), side)
        canvas.drawBitmap(source, srcRect, Rect(d[0], d[1], d[2], d[3]), Paint().apply { isFilterBitmap = false })
        source.recycle()
        return out
    }

    /**
     * One-off for QRs saved before cropping existed: crops each file in place. Safe to run again,
     * a file that is already just the QR comes back the same size.
     */
    suspend fun recropSaved() = withContext(Dispatchers.IO) {
        for (qr in dao.all()) {
            val original = BitmapFactory.decodeFile(qr.filePath) ?: continue
            val cropped = cropToQr(original)
            File(qr.filePath).outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cropped.recycle()
            cache.remove(qr.filePath)
        }
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

/** Bridges a Play Services Task into a coroutine without an extra library. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
