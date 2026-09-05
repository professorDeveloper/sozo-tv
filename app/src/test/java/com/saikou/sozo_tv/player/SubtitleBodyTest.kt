package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.domain.player.SubtitleBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SubtitleBodyTest {
    private val srt = "1\n00:00:01,000 --> 00:00:02,000\nHello\n"

    @Test
    fun `plain utf8 is returned unchanged`() {
        val body = SubtitleBody.decode(srt.toByteArray(Charsets.UTF_8), "English")

        assertEquals(srt, body)
    }

    @Test
    fun `a gzipped srt is unwrapped`() {
        val body = SubtitleBody.decode(gzip(srt.toByteArray()), "English")

        assertEquals(srt, body)
    }

    @Test
    fun `a zip yields the subtitle beside the extras a release ships with`() {
        val zipped = zip(
            "readme.txt" to "visit our site".toByteArray(),
            "Movie.2019.1080p.srt" to srt.toByteArray(),
            "poster.jpg" to ByteArray(4_000),
        )

        assertEquals(srt, SubtitleBody.decode(zipped, "English"))
    }

    @Test
    fun `a zip of one unnamed entry still yields something`() {
        assertEquals(srt, SubtitleBody.decode(zip("subtitle" to srt.toByteArray()), null))
    }

    @Test
    fun `arabic windows-1256 is read as arabic and not as replacement characters`() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nمرحبا بالعالم\n"
        val bytes = text.toByteArray(Charset.forName("windows-1256"))

        val body = SubtitleBody.decode(bytes, "Arabic")

        assertEquals(text, body)
        assertFalse(body.contains('�'))
    }

    @Test
    fun `cyrillic windows-1251 is read from a three-letter language code`() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nПривет, мир\n"
        val bytes = text.toByteArray(Charset.forName("windows-1251"))

        assertEquals(text, SubtitleBody.decode(bytes, "rus"))
    }

    @Test
    fun `japanese shift-jis is recognised with no language declared at all`() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nこんにちは世界です、元気ですか\n"
        val bytes = text.toByteArray(Charset.forName("Shift_JIS"))

        assertEquals(Charset.forName("Shift_JIS"), SubtitleBody.charsetFor(bytes, null))
        assertEquals(text, SubtitleBody.decode(bytes, null))
    }

    @Test
    fun `valid utf8 is never second-guessed into the language's legacy codepage`() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nمرحبا\n"
        val bytes = text.toByteArray(Charsets.UTF_8)

        assertEquals(Charsets.UTF_8, SubtitleBody.charsetFor(bytes, "Arabic"))
        assertEquals(text, SubtitleBody.decode(bytes, "Arabic"))
    }

    @Test
    fun `a utf8 bom is stripped rather than shown as a cue`() {
        val bytes = "﻿$srt".toByteArray(Charsets.UTF_8)

        assertEquals(srt, SubtitleBody.decode(bytes, null))
    }

    @Test
    fun `utf16 little endian is decoded from its bom`() {
        val bytes = "﻿$srt".toByteArray(Charsets.UTF_16LE)

        assertEquals(srt, SubtitleBody.decode(bytes, null))
    }

    @Test
    fun `accented latin text is not mistaken for a double-byte encoding`() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nIl était une fois, à Paris\n"
        val bytes = text.toByteArray(Charset.forName("windows-1252"))

        assertTrue(SubtitleBody.decode(bytes, null).contains("était"))
    }

    @Test
    fun `a body that is neither compressed nor text survives to be counted as zero cues`() {
        val html = "<html><body>403</body></html>"

        assertEquals(html, SubtitleBody.decode(html.toByteArray(), "English"))
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(bytes) }
        }.toByteArray()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
}
