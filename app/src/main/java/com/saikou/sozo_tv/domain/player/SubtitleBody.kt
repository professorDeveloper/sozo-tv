package com.saikou.sozo_tv.domain.player

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

object SubtitleBody {

    fun decode(bytes: ByteArray, languageHint: String? = null): String {
        val body = unwrap(bytes)
        val bom = bomCharset(body)
        if (bom != null) return String(body, bom.second, body.size - bom.second, bom.first)
        return String(body, charsetFor(body, languageHint))
    }

    fun charsetFor(bytes: ByteArray, languageHint: String? = null): Charset {
        bomCharset(bytes)?.let { return it.first }
        if (isUtf8(bytes)) return Charsets.UTF_8
        legacyCharsetFor(languageHint)?.let { return it }
        return guessFromBytes(bytes)
    }

    fun unwrap(bytes: ByteArray): ByteArray = when {
        startsWith(bytes, GZIP_MAGIC) -> runCatching {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
        }.getOrDefault(bytes)

        startsWith(bytes, ZIP_MAGIC) -> runCatching { unzip(bytes) }.getOrNull() ?: bytes
        else -> bytes
    }

    private fun unzip(bytes: ByteArray): ByteArray? {
        var best: ByteArray? = null
        var bestNamed = false
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val named = entry.name.substringAfterLast('.', "").lowercase() in EXTENSIONS
                val content = ByteArrayOutputStream().also { zip.copyTo(it) }.toByteArray()
                val better = when {
                    named && !bestNamed -> true
                    named == bestNamed -> content.size > (best?.size ?: -1)
                    else -> false
                }
                if (better) {
                    best = content
                    bestNamed = named
                }
            }
        }
        return best
    }

    private fun bomCharset(bytes: ByteArray): Pair<Charset, Int>? = when {
        startsWith(bytes, UTF8_BOM) -> Charsets.UTF_8 to 3
        startsWith(bytes, UTF16_LE_BOM) -> Charsets.UTF_16LE to 2
        startsWith(bytes, UTF16_BE_BOM) -> Charsets.UTF_16BE to 2
        else -> null
    }

    private fun isUtf8(bytes: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }

    private fun legacyCharsetFor(hint: String?): Charset? {
        val key = hint?.trim()?.lowercase()?.takeWhile { it.isLetter() }?.takeIf { it.length >= 2 }
            ?: return null
        val name = CODEPAGES.entries.firstOrNull { (languages, _) -> key in languages }?.value
            ?: return null
        return charsetOrNull(name)
    }

    private fun guessFromBytes(bytes: ByteArray): Charset {
        if (looksShiftJis(bytes)) charsetOrNull("Shift_JIS")?.let { return it }
        if (hasHighByteRuns(bytes)) charsetOrNull("windows-1251")?.let { return it }
        return charsetOrNull("windows-1252") ?: Charsets.ISO_8859_1
    }

    private fun looksShiftJis(bytes: ByteArray): Boolean {
        var pairs = 0
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> i++
                b in 0xA1..0xDF -> i++
                b in 0x81..0x9F || b in 0xE0..0xEF -> {
                    val next = (bytes.getOrNull(i + 1)?.toInt() ?: 0) and 0xFF
                    if (next !in 0x40..0x7E && next !in 0x80..0xFC) return false
                    pairs++
                    i += 2
                }

                else -> return false
            }
        }
        return pairs > 0 && pairs * 2 * 10 >= bytes.size
    }

    private fun hasHighByteRuns(bytes: ByteArray): Boolean {
        var run = 0
        for (b in bytes) {
            if ((b.toInt() and 0xFF) >= 0xC0) {
                run++
                if (run >= HIGH_BYTE_RUN) return true
            } else {
                run = 0
            }
        }
        return false
    }

    private fun charsetOrNull(name: String): Charset? =
        runCatching { if (Charset.isSupported(name)) Charset.forName(name) else null }.getOrNull()

    private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean {
        if (bytes.size < prefix.size) return false
        return prefix.indices.all { bytes[it] == prefix[it] }
    }

    private val GZIP_MAGIC = byteArrayOf(0x1F, 0x8B.toByte())
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    private val EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub", "txt")

    private const val HIGH_BYTE_RUN = 6

    /** The language hint is the only thing that separates these — the byte ranges overlap. */
    private val CODEPAGES: Map<Set<String>, String> = mapOf(
        setOf("ar", "ara", "arabic", "fa", "fas", "per", "persian", "farsi", "ur", "urd", "urdu")
                to "windows-1256",
        setOf(
            "ru", "rus", "russian", "uk", "ukr", "ukrainian", "bg", "bul", "bulgarian",
            "sr", "srp", "serbian", "mk", "mkd", "macedonian", "be", "bel", "belarusian"
        ) to "windows-1251",
        setOf("ja", "jpn", "japanese") to "Shift_JIS",
        setOf("zh", "zho", "chi", "chinese") to "GBK",
        setOf("ko", "kor", "korean") to "EUC-KR",
        setOf("el", "ell", "gre", "greek") to "windows-1253",
        setOf("he", "heb", "iw", "hebrew") to "windows-1255",
        setOf("th", "tha", "thai") to "windows-874",
        setOf("tr", "tur", "turkish") to "windows-1254",
        setOf("vi", "vie", "vietnamese") to "windows-1258",
        setOf(
            "pl", "pol", "polish", "cs", "ces", "cze", "czech", "hu", "hun", "hungarian",
            "ro", "ron", "rum", "romanian", "hr", "hrv", "croatian", "sk", "slk", "slovak",
            "sl", "slv", "slovenian", "sq", "sqi", "albanian", "bs", "bos", "bosnian"
        ) to "windows-1250",
    )
}
