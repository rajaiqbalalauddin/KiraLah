package com.buyless.app.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What should be sent: the pay card picture, the message, and whose chat to open (null = let the user pick). */
data class ShareRequest(val image: Uri?, val text: String, val phone: String?)

/** How the hand-off went, so the UI can mark the person as sent or explain what happened. */
enum class SendResult { WHATSAPP, SHARE_SHEET, FAILED }

/**
 * Hands a pay card to WhatsApp on this phone. Nothing is sent automatically: WhatsApp opens with the
 * picture and message ready, and the user taps Send. That keeps it free and on their own number.
 *
 * Opening a specific person's chat with a picture attached uses WhatsApp's "jid" extra. It is not
 * officially documented, so there are two fallbacks: if WhatsApp ignores it, WhatsApp shows its own
 * chat picker; if WhatsApp is not installed, the normal Android share sheet opens instead.
 */
object WhatsAppSender {

    /** Regular WhatsApp first, then WhatsApp Business. Both are declared in the manifest's <queries>. */
    private val packages = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun installedPackage(context: Context): String? = packages.firstOrNull { pkg ->
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun send(context: Context, request: ShareRequest): SendResult {
        val intent = Intent(Intent.ACTION_SEND).apply {
            if (request.image != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, request.image)
                // ClipData carries the read grant to the receiving app on every Android version.
                clipData = ClipData.newRawUri("pay card", request.image)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, request.text)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pkg = installedPackage(context)
        if (pkg != null) {
            val direct = Intent(intent).apply {
                setPackage(pkg)
                request.phone?.let { putExtra("jid", "$it@s.whatsapp.net") }
            }
            try {
                context.startActivity(direct)
                return SendResult.WHATSAPP
            } catch (e: ActivityNotFoundException) {
                // Fall through to the share sheet.
            } catch (e: SecurityException) {
                // Same.
            }
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Send pay card").apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            SendResult.SHARE_SHEET
        } catch (e: ActivityNotFoundException) {
            SendResult.FAILED
        }
    }
}

/**
 * Renders pay cards to PNG files the chat app can read. Files live in cache/share and are overwritten
 * per person, so they never pile up; Android may clear them any time after sending.
 */
class PayCardFiles(private val context: Context) {

    private val dir: File get() = File(context.cacheDir, "share").apply { mkdirs() }

    /** Full-size QR from app storage. Decoded fresh because the UI cache holds Compose bitmaps. */
    suspend fun loadQr(path: String?): Bitmap? = withContext(Dispatchers.IO) {
        path?.let { BitmapFactory.decodeFile(it) }
    }

    suspend fun save(name: String, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
