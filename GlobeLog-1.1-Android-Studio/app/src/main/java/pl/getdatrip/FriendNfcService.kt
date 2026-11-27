package pl.getdatrip

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.SystemClock

internal object FriendNfcCard {
    private val aid = byteArrayOf(0xF0.toByte(), 0x47, 0x4C, 0x4F, 0x42, 0x45, 0x4C, 0x4F, 0x47)
    val selectCommand = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid
    private val success = byteArrayOf(0x90.toByte(), 0x00)
    private val missing = byteArrayOf(0x6A, 0x82.toByte())
    private val codePattern = Regex("[A-HJ-NP-Z2-9]{10}")

    private data class Offer(val code: String, val validUntil: Long)

    @Volatile private var offer: Offer? = null

    fun show(code: String) {
        if (codePattern.matches(code)) offer = Offer(code, SystemClock.elapsedRealtime() + 60_000)
    }

    fun hide() {
        offer = null
    }

    fun response(command: ByteArray): ByteArray {
        val current = offer ?: return missing
        if (SystemClock.elapsedRealtime() >= current.validUntil) return missing
        val isSelect = command.contentEquals(selectCommand) ||
            command.contentEquals(selectCommand + 0.toByte())
        if (!isSelect) return missing
        return current.code.toByteArray(Charsets.US_ASCII) + success
    }

    fun read(response: ByteArray): String? {
        if (response.size != 12 || !response.copyOfRange(10, 12).contentEquals(success)) return null
        return response.copyOfRange(0, 10).toString(Charsets.US_ASCII).takeIf(codePattern::matches)
    }
}

class FriendNfcService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray =
        FriendNfcCard.response(commandApdu)

    override fun onDeactivated(reason: Int) = Unit
}
