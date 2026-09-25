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

/**
 * On-device OCR with ML Kit (bundled model, so it works offline and the receipt never leaves the phone).
 * One recognizer is created lazily and reused: loading the model is the slow part, so doing it once per
 * process makes the second scan much faster than the first.
 */
class ReceiptScanner(private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Reads a receipt photo and returns the parsed items and charges. */
    suspend fun scan(uri: Uri): ParsedReceipt = withContext(Dispatchers.Default) {
        // fromFilePath also applies the photo's EXIF rotation, so sideways camera shots still read.
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
}

/** Bridges a Play Services Task into a coroutine without an extra library. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
