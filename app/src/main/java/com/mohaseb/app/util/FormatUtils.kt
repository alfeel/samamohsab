package com.mohaseb.app.util

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object FormatUtils {

    private val decimalFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.ENGLISH))

    fun formatMoney(amount: Double, currency: String = "ر.س"): String {
        return "${decimalFormat.format(amount)} $currency"
    }

    fun formatNumber(number: Double): String {
        return if (number % 1.0 == 0.0) {
            number.toInt().toString()
        } else {
            decimalFormat.format(number)
        }
    }
}

object ZatcaQrGenerator {

    /**
     * ينشئ كود الاستجابة السريعة المتوافق مع هيئة الزكاة والضريبة والجمارك (ZATCA TLV Base64)
     */
    fun generateZatcaTlvBase64(
        sellerName: String,
        vatNumber: String,
        invoiceDate: String,
        totalWithVat: Double,
        vatAmount: Double
    ): String {
        val bos = ByteArrayOutputStream()

        fun writeTlv(tag: Byte, value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            bos.write(tag.toInt())
            bos.write(bytes.size)
            bos.write(bytes)
        }

        writeTlv(1.toByte(), sellerName.ifBlank { "محاسب سوفت" })
        writeTlv(2.toByte(), vatNumber.ifBlank { "300000000000003" })
        writeTlv(3.toByte(), invoiceDate)
        writeTlv(4.toByte(), String.format(Locale.ENGLISH, "%.2f", totalWithVat))
        writeTlv(5.toByte(), String.format(Locale.ENGLISH, "%.2f", vatAmount))

        return android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
    }
}
