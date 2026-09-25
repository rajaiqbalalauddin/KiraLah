package com.buyless.app.split

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Why a Gemini call failed, so the UI can say something useful and fall back to on-device reading. */
class GeminiException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind { NO_NETWORK, BAD_KEY, RATE_LIMITED, SERVER, NOT_A_RECEIPT, BAD_RESPONSE }
}

/**
 * Reads a receipt photo with Gemini and returns structured items and charges.
 *
 * Speed vs accuracy: the fast model (Flash-Lite) reads first. Its answer is checked against the
 * printed total; only if the numbers do not add up is the photo sent again to the stronger Flash
 * model. Most clear receipts finish on the first, cheaper call; hard ones still come out right.
 *
 * Everything uses the platform's HttpURLConnection and org.json, so no networking library is added.
 */
class GeminiReceiptReader(private val context: Context, private val apiKey: String) {

    enum class Pass { FAST, ACCURATE }

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** Shrinks and rotates the photo once, then reuses the bytes for both passes. */
    suspend fun prepare(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decodeUpright(uri, MAX_SIDE_PX) ?: throw GeminiException(GeminiException.Kind.BAD_RESPONSE, "Could not open the photo")
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun read(imageBase64: String, pass: Pass): ParsedReceipt = withContext(Dispatchers.IO) {
        val model = if (pass == Pass.FAST) FAST_MODEL else ACCURATE_MODEL
        val body = requestBody(imageBase64, withThinking = pass == Pass.ACCURATE)
        val text = try {
            post(model, body)
        } catch (e: GeminiException) {
            // If the stronger model rejects the thinking setting, retry once without it.
            if (e.kind == GeminiException.Kind.BAD_RESPONSE && pass == Pass.ACCURATE) {
                post(model, requestBody(imageBase64, withThinking = false))
            } else {
                throw e
            }
        }
        GeminiReceiptJson.toReceipt(text)
    }

    private fun post(model: String, body: String): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val response = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            when (code) {
                in 200..299 -> return extractText(response)
                400 -> throw GeminiException(GeminiException.Kind.BAD_RESPONSE, "Request rejected: ${response.take(200)}")
                401, 403 -> throw GeminiException(GeminiException.Kind.BAD_KEY, "Gemini API key was rejected")
                429 -> throw GeminiException(GeminiException.Kind.RATE_LIMITED, "Gemini is busy or out of quota")
                else -> throw GeminiException(GeminiException.Kind.SERVER, "Gemini error $code")
            }
        } catch (e: SocketTimeoutException) {
            throw GeminiException(GeminiException.Kind.NO_NETWORK, "Gemini took too long to answer")
        } catch (e: GeminiException) {
            throw e
        } catch (e: IOException) {
            throw GeminiException(GeminiException.Kind.NO_NETWORK, "No internet connection")
        } finally {
            conn.disconnect()
        }
    }

    /** Joins the answer text, skipping any "thought" parts the model may return. */
    private fun extractText(response: String): String {
        val parts = JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw GeminiException(GeminiException.Kind.BAD_RESPONSE, "Empty answer from Gemini")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            sb.append(part.optString("text"))
        }
        return sb.toString()
    }

    private fun requestBody(imageBase64: String, withThinking: Boolean): String {
        val image = JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", imageBase64))
        val prompt = JSONObject().put("text", PROMPT)
        val config = JSONObject()
            .put("responseMimeType", "application/json")
            .put("responseJsonSchema", JSONObject(SCHEMA))
        if (withThinking) config.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(image).put(prompt))))
            .put("generationConfig", config)
            .toString()
    }

    /** Decodes at a power-of-two size close to [maxSide], then applies the camera's EXIF rotation. */
    private fun decodeUpright(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (resolver.openInputStream(uri) ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = (resolver.openInputStream(uri) ?: return null).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees() } ?: 0
        } catch (e: IOException) {
            0
        }
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        if (rotation == 0 && scale >= 1f) return decoded
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private fun ExifInterface.rotationDegrees(): Int = when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    companion object {
        /** Fast first read. Swap model ids here when Google releases newer ones. */
        const val FAST_MODEL = "gemini-3.5-flash-lite"

        /** Second opinion when the fast read does not add up to the printed total. */
        const val ACCURATE_MODEL = "gemini-3.8-flash"

        // 1600 px on the long side keeps small print readable while the upload stays around 200-400 KB.
        private const val MAX_SIDE_PX = 1600
        private const val JPEG_QUALITY = 85

        private val PROMPT = """
            You read Malaysian restaurant and shop receipts for a bill-splitting app.
            Extract every purchased line exactly as printed. Rules:
            - items: one entry per purchased line. name = the item text as printed (keep brand and size words).
              quantity = printed quantity (1 if none). lineTotal = the amount in the price column for that line,
              already multiplied by quantity. unitPrice = price per unit if printed, else lineTotal / quantity.
            - Add-ons or extras that have their own price (e.g. "+ Telur 1.50") are separate items.
              Modifiers without a price (e.g. "Less ice", "No onion", "Takeaway") go in that item's note.
            - A discount printed under a single item: subtract it from that item's lineTotal.
              Bill-level discounts, vouchers and member savings go in discount as a positive number.
            - serviceCharge, tax (SST, GST, service tax), rounding (can be negative) and total come from the summary lines.
              total is the final amount the customer paid (e.g. "Total Incl. SST", "Grand Total"), never a
              "Total Excl." line, "Total Items" or "Total Qty". Ignore the tax summary table at the bottom.
            - pricesIncludeTax: true when the item prices already contain the tax. Signs: "Incl. SST" /
              "inclusive" wording, or the items already add up to the final total while a tax line is still shown.
              pricesIncludeService: the same, for the service charge.
            - Never list payment or change lines (cash, card, e-wallet, change, tendered) as items.
            - All amounts are plain numbers in the receipt currency, no symbols.
            - If a value is not printed, use 0. Never invent items or prices.
            - If the photo is not a receipt or bill, set isReceipt to false and return no items.
        """.trimIndent()

        private const val SCHEMA = """
            {
              "type": "object",
              "properties": {
                "isReceipt": {"type": "boolean"},
                "merchant": {"type": "string"},
                "currency": {"type": "string"},
                "items": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": {"type": "string"},
                      "quantity": {"type": "number"},
                      "unitPrice": {"type": "number"},
                      "lineTotal": {"type": "number"},
                      "note": {"type": "string"}
                    },
                    "required": ["name", "quantity", "lineTotal"]
                  }
                },
                "subtotal": {"type": "number"},
                "serviceCharge": {"type": "number"},
                "tax": {"type": "number"},
                "rounding": {"type": "number"},
                "discount": {"type": "number"},
                "total": {"type": "number"},
                "pricesIncludeTax": {"type": "boolean"},
                "pricesIncludeService": {"type": "boolean"}
              },
              "required": ["isReceipt", "items", "serviceCharge", "tax", "rounding", "discount", "total"]
            }
        """
    }
}

/**
 * Turns Gemini's JSON answer into a ParsedReceipt. Kept separate from networking so the mapping rules
 * (rounding to sen, dropping empty lines) are easy to read and test.
 */
object GeminiReceiptJson {

    fun toReceipt(text: String): ParsedReceipt {
        val json = try {
            JSONObject(text.trim().removePrefix("```json").removeSuffix("```").trim())
        } catch (e: Exception) {
            throw GeminiException(GeminiException.Kind.BAD_RESPONSE, "Gemini did not return valid JSON")
        }
        if (!json.optBoolean("isReceipt", true)) {
            throw GeminiException(GeminiException.Kind.NOT_A_RECEIPT, "That photo does not look like a receipt")
        }
        val items = ArrayList<ReceiptItem>()
        val array = json.optJSONArray("items") ?: JSONArray()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            val qty = o.optDouble("quantity", 1.0).let { if (it.isNaN() || it < 1) 1 else it.toInt() }
            val line = sen(o, "lineTotal") ?: sen(o, "unitPrice")?.times(qty) ?: continue
            if (name.isEmpty() || line <= 0) continue
            items += ReceiptItem(name = name, qty = qty, priceSen = line, note = o.optString("note").trim().ifEmpty { null })
        }
        val parsed = ParsedReceipt(
            items = items,
            serviceSen = (sen(json, "serviceCharge") ?: 0L).coerceAtLeast(0),
            taxSen = (sen(json, "tax") ?: 0L).coerceAtLeast(0),
            roundingSen = sen(json, "rounding") ?: 0L,
            discountSen = kotlin.math.abs(sen(json, "discount") ?: 0L),
            totalSen = sen(json, "total")?.takeIf { it > 0 },
            merchant = json.optString("merchant").trim().ifEmpty { null },
        )
        // Gemini's flags are a hint; the printed total has the final say (see ChargeReconciler).
        return ChargeReconciler.reconcile(
            parsed,
            hintTaxIncluded = if (json.has("pricesIncludeTax")) json.optBoolean("pricesIncludeTax") else null,
            hintServiceIncluded = if (json.has("pricesIncludeService")) json.optBoolean("pricesIncludeService") else null,
        )
    }

    /** Ringgit number to sen, rounded half-up so 12.345 becomes 1235 rather than drifting. */
    private fun sen(o: JSONObject, key: String): Long? {
        if (!o.has(key) || o.isNull(key)) return null
        val value = o.optDouble(key, Double.NaN)
        if (value.isNaN()) return null
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
    }
}
