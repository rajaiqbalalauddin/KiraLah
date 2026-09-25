package com.buyless.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.buyless.app.data.db.SplitBillDao
import com.buyless.app.data.db.SplitBillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Everything needed to reopen a split exactly as it was left. Plain data, no Compose types. */
data class SplitSnapshot(
    val merchant: String?,
    val people: List<SnapPerson>,
    val items: List<SnapItem>,
    val serviceText: String,
    val taxText: String,
    val roundingText: String,
    val discountText: String,
    val receiptTotalSen: Long?,
    val paid: Set<Long>,
    /** Defaults keep older saved splits (from before this field existed) loading as "added on top". */
    val serviceIncluded: Boolean = false,
    val taxIncluded: Boolean = false,
    /** People whose WhatsApp message was opened, so the Sent tag survives reopening the bill. */
    val sent: Set<Long> = emptySet(),
) {
    /** friendId and phone are null for bills saved before friends existed. */
    data class SnapPerson(val id: Long, val name: String, val colorIndex: Int, val friendId: Long? = null, val phone: String? = null)
    data class SnapItem(val id: Long, val name: String, val priceSen: Long, val owners: List<Long>, val note: String?)
}

/**
 * Split history. Saves the board as JSON, keeps a private copy of the receipt photo (the camera
 * file lives in cache and a gallery link can disappear), and caches small thumbnails for the list.
 */
class SplitHistoryRepository(private val context: Context, private val dao: SplitBillDao) {

    private val dir: File get() = File(context.filesDir, "receipts").apply { mkdirs() }
    private val thumbs = LruCache<String, ImageBitmap>(24)

    fun observeAll(): Flow<List<SplitBillEntity>> = dao.observeAll().distinctUntilChanged()

    suspend fun get(id: Long): SplitBillEntity? = dao.get(id)

    suspend fun save(entity: SplitBillEntity): Long = dao.upsert(entity)

    /** Deletes the row but keeps the photo, so Undo can bring the whole thing back. */
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun restore(entity: SplitBillEntity) {
        dao.upsert(entity)
    }

    /** Removes photos no bill points to any more. Run occasionally, never while Undo is showing. */
    suspend fun cleanOrphanPhotos() = withContext(Dispatchers.IO) {
        val used = HashSet<String>()
        dao.allReceiptPaths().forEach { used += it }
        dir.listFiles()?.forEach { if (it.absolutePath !in used) it.delete() }
    }

    /** Stores a downscaled, upright JPEG copy of the receipt. Returns its path, or null if unreadable. */
    suspend fun keepReceipt(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
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
            val upright = if (rotation == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Small preview for history cards. Cached, so scrolling the list never re-decodes. */
    suspend fun thumbnail(path: String): ImageBitmap? {
        thumbs.get(path)?.let { return it }
        val image = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }
        if (image != null) thumbs.put(path, image)
        return image
    }

    /** Full-size photo for the receipt viewer. Not cached: it is large and opened rarely. */
    suspend fun fullImage(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(path)?.asImageBitmap()
    }

    companion object {
        private const val MAX_SIDE = 2000

        fun encode(s: SplitSnapshot): String = JSONObject().apply {
            put("merchant", s.merchant ?: JSONObject.NULL)
            put("people", JSONArray().apply {
                s.people.forEach {
                    put(
                        JSONObject().put("id", it.id).put("name", it.name).put("color", it.colorIndex)
                            .put("friendId", it.friendId ?: JSONObject.NULL).put("phone", it.phone ?: JSONObject.NULL),
                    )
                }
            })
            put("items", JSONArray().apply {
                s.items.forEach { i ->
                    put(
                        JSONObject().put("id", i.id).put("name", i.name).put("price", i.priceSen)
                            .put("owners", JSONArray().apply { i.owners.forEach { put(it) } })
                            .put("note", i.note ?: JSONObject.NULL),
                    )
                }
            })
            put("service", s.serviceText)
            put("tax", s.taxText)
            put("rounding", s.roundingText)
            put("discount", s.discountText)
            put("receiptTotal", s.receiptTotalSen ?: JSONObject.NULL)
            put("serviceIncluded", s.serviceIncluded)
            put("taxIncluded", s.taxIncluded)
            put("sent", JSONArray().apply { s.sent.forEach { put(it) } })
            put("paid", JSONArray().apply { s.paid.forEach { put(it) } })
        }.toString()

        fun decode(json: String): SplitSnapshot {
            val o = JSONObject(json)
            val people = o.optJSONArray("people") ?: JSONArray()
            val items = o.optJSONArray("items") ?: JSONArray()
            val paid = o.optJSONArray("paid") ?: JSONArray()
            return SplitSnapshot(
                merchant = if (o.isNull("merchant")) null else o.optString("merchant"),
                people = (0 until people.length()).map { i ->
                    val p = people.getJSONObject(i)
                    SplitSnapshot.SnapPerson(
                        id = p.getLong("id"),
                        name = p.getString("name"),
                        colorIndex = p.optInt("color"),
                        friendId = if (p.isNull("friendId")) null else p.optLong("friendId"),
                        phone = if (p.isNull("phone")) null else p.optString("phone"),
                    )
                },
                items = (0 until items.length()).map { i ->
                    val it = items.getJSONObject(i)
                    val owners = it.optJSONArray("owners") ?: JSONArray()
                    SplitSnapshot.SnapItem(
                        id = it.getLong("id"),
                        name = it.getString("name"),
                        priceSen = it.getLong("price"),
                        owners = (0 until owners.length()).map { k -> owners.getLong(k) },
                        note = if (it.isNull("note")) null else it.optString("note"),
                    )
                },
                serviceText = o.optString("service"),
                taxText = o.optString("tax"),
                roundingText = o.optString("rounding"),
                discountText = o.optString("discount"),
                receiptTotalSen = if (o.isNull("receiptTotal")) null else o.optLong("receiptTotal"),
                paid = (0 until paid.length()).map { paid.getLong(it) }.toSet(),
                serviceIncluded = o.optBoolean("serviceIncluded", false),
                taxIncluded = o.optBoolean("taxIncluded", false),
                sent = o.optJSONArray("sent")?.let { a -> (0 until a.length()).map { a.getLong(it) }.toSet() } ?: emptySet(),
            )
        }
    }
}
