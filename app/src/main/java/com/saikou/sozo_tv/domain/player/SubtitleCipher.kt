package com.saikou.sozo_tv.domain.player

import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Some sources (KissKH's `.txt1` files) ship an ordinary SRT whose cue text lines are
 * each AES-encrypted; the backend tags such a subtitle with the key to open them.
 */
data class SubtitleCipher(
    val alg: String,
    val key: String,
    val iv: String,
    val scope: String = SCOPE,
    val encoding: String = ENCODING,
) {

    /** Decrypts every cue text line; indices, timing lines and blanks pass through. */
    fun decrypt(body: String): String {
        if (!alg.equals(ALG, true) || !scope.equals(SCOPE, true) || !encoding.equals(ENCODING, true)) {
            throw IOException("unsupported subtitle cipher $alg/$scope/$encoding")
        }
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val ivBytes = iv.toByteArray(Charsets.UTF_8)
        if (keyBytes.size != 16 || ivBytes.size != 16) throw IOException("bad subtitle cipher key")
        val aes = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secret = SecretKeySpec(keyBytes, "AES")

        var encrypted = 0
        var opened = 0
        val out = body.split('\n').map { line ->
            val text = line.trim()
            if (!isCueText(text)) return@map line
            encrypted++
            val plain = runCatching {
                aes.init(Cipher.DECRYPT_MODE, secret, IvParameterSpec(ivBytes))
                val bytes = text.decodeBase64() ?: return@runCatching null
                strictUtf8(aes.doFinal(bytes.toByteArray()))
            }.getOrNull() ?: return@map line
            opened++
            if (line.endsWith('\r')) "$plain\r" else plain
        }
        // A wrong key still yields valid padding now and then, so a few opened lines prove nothing.
        if (encrypted > 0 && opened * 2 < encrypted) throw IOException("subtitle cipher opened $opened of $encrypted cues")
        return out.joinToString("\n")
    }

    private fun strictUtf8(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()

    private fun isCueText(line: String): Boolean =
        line.isNotEmpty() && !line.contains("-->") && !line.all { it.isDigit() } && line != "WEBVTT"

    companion object {
        private const val ALG = "aes-128-cbc"
        private const val SCOPE = "cue-text"
        private const val ENCODING = "base64"

        fun from(o: JSONObject?): SubtitleCipher? {
            o ?: return null
            val alg = o.optString("alg").ifEmpty { return null }
            val key = o.optString("key").ifEmpty { return null }
            val iv = o.optString("iv").ifEmpty { return null }
            return SubtitleCipher(
                alg, key, iv,
                o.optString("scope").ifEmpty { SCOPE },
                o.optString("encoding").ifEmpty { ENCODING },
            )
        }
    }
}
