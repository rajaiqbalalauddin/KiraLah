package com.buyless.app.parser

import com.buyless.app.data.model.Category
import com.buyless.app.data.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

// Real Bank Islam alert formats (from the BIMB inbox, Sep 2026). Account and card digits were already
// masked by the bank. Add a test here for every new BIMB wording that turns up in Notification samples.
class BimbProfileTest {

    private val zone = ZoneId.of("Asia/Kuala_Lumpur")

    private fun parse(title: String?, body: String) =
        NotificationParser.parse(title, body, BankProfiles.BIMB, zone)

    private fun parsed(title: String?, body: String) = parse(title, body) as NotificationParser.Result.Parsed

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test fun qrPayment() {
        val r = parsed("TRANSACTION - PAYMENT SUCCESSFUL", "Your QR payment of RM6.50 to Rafa****ures on 21 Sep 2026 06:50:28 PM is successful.")
        assertEquals(650L, r.amountSen)
        assertEquals(Direction.OUT, r.direction)
        assertEquals("Rafa****ures", r.merchant)
        assertEquals(at("2026-09-21T18:50:28"), r.occurredAt)
        assertTrue(r.exact)
    }

    @Test fun fpxPayment() {
        val r = parsed("TRANSACTION - PAYMENT SUCCESSFUL", "Your FPX payment of RM9.90 to SHOPEE MALAYSIA on 24 Sep 2026 10:31:26 AM is successful.")
        assertEquals(990L, r.amountSen)
        assertEquals("Shopee Malaysia", r.merchant)
        assertEquals(Category.SHOPPING, r.category)
    }

    @Test fun bigFpxPayment() {
        assertEquals(100000L, parsed(null, "Your FPX payment of RM1,000.00 to PACIFIC TRUSTEES BERHAD on 21 Sep 2026 05:51:29 PM is successful.").amountSen)
    }

    @Test fun qrTransfer() {
        val r = parsed("TRANSACTION - TRANSFER SUCCESSFUL", "Your QR transfer of RM9.90 to HH R****RISE on 23 Sep 2026 07:49:31 PM is successful.")
        assertEquals(990L, r.amountSen)
        assertEquals("HH R****RISE", r.merchant)
    }

    @Test fun cardInRinggit() {
        val r = parsed(
            "TRANSACTION - BANK ISLAM CARD ALERT",
            "YOU HAVE PERFORMED A TRANSACTION OF RM 18.50 AT KOMUGI-KUCHAI E KUALA LUMPUR ON 20 SEP 26, 16:22:26. IF THIS WAS NOT YOU, PLEASE CONTACT BANK ISLAM.",
        )
        assertEquals(1850L, r.amountSen)
        assertEquals(at("2026-09-20T16:22:26"), r.occurredAt)
        assertEquals(Category.FOOD, r.category)
    }

    @Test fun cardInForeignCurrencyGoesToQuickCheck() {
        val r = parsed(
            "TRANSACTION - BANK ISLAM CARD ALERT",
            "YOU HAVE PERFORMED A TRANSACTION OF USD21.60 AT OPENAI* CHATGPT +14158799686 ON 20 SEP 26, 17:44:01. IF THIS WAS NOT YOU, PLEASE CONTACT BANK ISLAM.",
        )
        assertNull(r.amountSen)
        assertEquals("USD 21.60", r.foreign)
        assertEquals("OPENAI* CHATGPT", r.merchant)
        assertEquals(Category.BILLS, r.category)
    }

    @Test fun duitNowReceived() {
        val r = parsed("TRANSACTION - BANK ISLAM TRANSACTION ALERT", "BIMB: RM103.50 received from *Maybank Berhad via DuitNow QR to account **0529 on 20Sep26 12:47:45.")
        assertEquals(10350L, r.amountSen)
        assertEquals(Direction.IN, r.direction)
        assertEquals("Maybank Berhad", r.merchant)
        assertEquals(at("2026-09-20T12:47:45"), r.occurredAt)
    }

    @Test fun malayCardUsage() {
        val r = parsed(
            "TRANSACTION - BANK ISLAM TRANSACTION ALERT",
            "BIMB: PENGGUNAAN KAD **1917 DI BASKIN-ROBBINS JAYA PETALING JAYA   MY RM34.90, 23Sep26 12:57:14. SILA HUBUNGI BANK ISLAM JIKA TIDAK SAH.",
        )
        assertEquals(3490L, r.amountSen)
        assertEquals(Direction.OUT, r.direction)
        assertEquals("Baskin-robbins Jaya Petaling Jaya", r.merchant)
        assertTrue(r.exact)
    }

    @Test fun promotionsAndUpdatesIgnored() {
        assertTrue(parse("PROMOTIONS - WASIAT SIAP, RM40 PUN DAPAT", "Mulakan perancangan wasiat anda dengan RM300 sahaja melalui BIMB Mobile.") is NotificationParser.Result.Ignore)
        assertTrue(parse("UPDATES - PENAMATAN PERKHIDMATAN PENDAFTARAN HAJI", "Berkuat kuasa 11 September 2026, perkhidmatan pendaftaran haji ditamatkan.") is NotificationParser.Result.Ignore)
    }
}
