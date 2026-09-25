package com.buyless.app.split

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What the scan is doing right now, shown by the loading animation. */
enum class ScanStage { PREPARING, READING, DOUBLE_CHECKING, ON_DEVICE }

/** Where the final answer came from, plus anything worth telling the user about it. */
data class ScanResult(val receipt: ParsedReceipt, val notice: String?)

/**
 * Reads a receipt photo. Gemini does the reading when an API key is set: it understands messy
 * layouts, add-ons and modifiers far better than plain OCR. If there is no key, no internet or
 * Gemini fails, the on-device ML Kit reader takes over so the Split tab still works offline.
 */
class ReceiptScanner(private val context: Context, apiKey: String) {

    private val gemini = GeminiReceiptReader(context, apiKey)
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun scan(uri: Uri, onStage: (ScanStage) -> Unit): ScanResult {
        if (!gemini.isConfigured) {
            onStage(ScanStage.ON_DEVICE)
            return ScanResult(onDevice(uri), "Read on this phone. Add a Gemini API key for smarter reading.")
        }
        return try {
            onStage(ScanStage.PREPARING)
            val image = gemini.prepare(uri)
            onStage(ScanStage.READING)
            val fast = gemini.read(image, GeminiReceiptReader.Pass.FAST)
            if (looksRight(fast)) return ScanResult(fast, null)

            // The fast read does not add up to the printed total: ask the stronger model once.
            onStage(ScanStage.DOUBLE_CHECKING)
            val accurate = runCatching { gemini.read(image, GeminiReceiptReader.Pass.ACCURATE) }.getOrNull()
            val best = listOfNotNull(fast, accurate).minByOrNull { it.mismatchSen ?: 0L } ?: fast
            ScanResult(best, null)
        } catch (e: GeminiException) {
            if (e.kind == GeminiException.Kind.NOT_A_RECEIPT) throw e
            onStage(ScanStage.ON_DEVICE)
            val reason = when (e.kind) {
                GeminiException.Kind.NO_NETWORK -> "No internet, so the receipt was read on this phone."
                GeminiException.Kind.BAD_KEY -> "The Gemini API key was rejected, so the receipt was read on this phone."
                GeminiException.Kind.RATE_LIMITED -> "Gemini is busy right now, so the receipt was read on this phone."
                else -> "Gemini could not read it, so the receipt was read on this phone."
            }
            ScanResult(onDevice(uri), "$reason Check the items carefully.")
        }
    }

    /** Good enough when there are items and, if a total is printed, everything adds up within 5 sen. */
    private fun looksRight(r: ParsedReceipt): Boolean =
        r.items.isNotEmpty() && (r.mismatchSen ?: 0L) <= TOLERANCE_SEN

    /** Offline fallback: ML Kit text recognition plus the row-based receipt parser. */
    private suspend fun onDevice(uri: Uri): ParsedReceipt = withContext(Dispatchers.Default) {
        val image = InputImage.fromFilePath(context, uri)
        val text = recognizer.process(image).await()
        val lines = ArrayList<OcrLine>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                lines += OcrLine(line.text, box.left, box.top, box.right, box.bottom)
            }
        }
        ReceiptParser.parse(ReceiptParser.groupRows(lines))
    }

    private companion object {
        const val TOLERANCE_SEN = 5L
    }
}

/** Bridges a Play Services Task into a coroutine without an extra library. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
