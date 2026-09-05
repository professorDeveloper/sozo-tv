package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.domain.player.SubtitleNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SubtitleNormalizerTest {
    @Test
    fun `srt commas become webvtt dots`() {
        val srt = "1\n00:00:06,376 --> 00:00:46,876\nHello there\n\n" +
                  "2\n00:01:12,200 --> 00:01:14,300\nSecond line\n"

        val r = SubtitleNormalizer.normalize(srt)

        assertEquals(SubtitleNormalizer.Kind.VTT, r.kind)
        assertEquals(2, r.cues)
        assertTrue(r.text.startsWith("WEBVTT\n\n"))
        assertTrue(r.text.contains("00:00:06.376 --> 00:00:46.876"))
        assertTrue(r.text.contains("00:01:12.200 --> 00:01:14.300"))
        assertFalse(r.text.lines().any { it.contains("-->") && it.contains(",") })
        assertTrue(r.text.contains("Hello there"))
    }

    @Test
    fun `index glued to the timestamp is still parsed`() {
        val broken = "51200:24:58,166 --> 00:24:59,250\nGlued index\n"

        val r = SubtitleNormalizer.normalize(broken)

        assertEquals(1, r.cues)
        assertTrue(r.text.contains("00:24:58.166 --> 00:24:59.250"))
        assertTrue(r.text.contains("Glued index"))
    }

    @Test
    fun `srt index lines are dropped, cue payload is not`() {
        val srt = "7\n00:00:01,000 --> 00:00:02,000\n42\n"

        val r = SubtitleNormalizer.normalize(srt)

        assertFalse(r.text.lines().contains("7"))
        assertTrue(r.text.contains("42"))
    }

    @Test
    fun `crlf and BOM do not break parsing`() {
        val srt = "﻿1\r\n00:00:03,500 --> 00:00:04,500\r\nCRLF line\r\n"

        val r = SubtitleNormalizer.normalize(srt)

        assertEquals(1, r.cues)
        assertTrue(r.text.contains("00:00:03.500 --> 00:00:04.500"))
        assertFalse(r.text.contains("\r"))
    }

    @Test
    fun `already valid webvtt keeps its cues and gains no duplicate header`() {
        val vtt = "WEBVTT\n\n00:00:05.000 --> 00:00:06.000\nAlready fine\n"

        val r = SubtitleNormalizer.normalize(vtt)

        assertEquals(1, r.cues)
        assertEquals(1, Regex("WEBVTT").findAll(r.text).count())
        assertTrue(r.text.contains("00:00:05.000 --> 00:00:06.000"))
    }

    @Test
    fun `ass is reported as ass rather than mangled into vtt`() {
        val ass = "[Script Info]\nTitle: x\n\n[Events]\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hi\n"

        val r = SubtitleNormalizer.normalize(ass)

        assertEquals(SubtitleNormalizer.Kind.SSA, r.kind)
        assertFalse(r.text.startsWith("WEBVTT"))
        assertEquals(1, r.cues)
    }

    @Test
    fun `an error page served as a subtitle reports no cues`() {
        val html = "<!DOCTYPE html>\n<html><body><h1>403 Forbidden</h1></body></html>\n"

        assertEquals(0, SubtitleNormalizer.normalize(html).cues)
    }

    @Test
    fun `hour-less webvtt timings are rewritten with hours and still counted`() {
        val vtt = "WEBVTT\n\n00:05.000 --> 00:06.000\nNo hours here\n"

        val r = SubtitleNormalizer.normalize(vtt)

        assertEquals(1, r.cues)
        assertTrue(r.text.contains("00:00:05.000 --> 00:00:06.000"))
        assertTrue(r.text.contains("No hours here"))
    }

    @Test
    fun `timestamps are ascii digits whatever the device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
            val r = SubtitleNormalizer.normalize("1\n00:00:06,376 --> 00:00:46,876\nHello\n")
            assertTrue(r.text.contains("00:00:06.376 --> 00:00:46.876"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `a positive offset moves every cue later`() {
        val srt = "1\n00:00:06,000 --> 00:00:08,000\nLate\n\n" +
                  "2\n00:01:00,000 --> 00:01:02,000\nAlso late\n"

        val r = SubtitleNormalizer.normalize(srt, offsetMs = 2_500)

        assertEquals(2, r.cues)
        assertTrue(r.text.contains("00:00:08.500 --> 00:00:10.500"))
        assertTrue(r.text.contains("00:01:02.500 --> 00:01:04.500"))
    }

    @Test
    fun `a negative offset cannot push a cue before zero`() {
        val srt = "1\n00:00:01,000 --> 00:00:04,000\nEarly\n"

        val r = SubtitleNormalizer.normalize(srt, offsetMs = -3_000)

        assertEquals(1, r.cues)
        assertTrue(r.text.contains("00:00:00.000 --> 00:00:01.000"))
    }

    @Test
    fun `an offset shifts ass dialogue lines and leaves the styles alone`() {
        val ass = "[Script Info]\nTitle: x\n\n[V4+ Styles]\nStyle: Default,Arial,20\n\n" +
                  "[Events]\nDialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,Hi\n"

        val r = SubtitleNormalizer.normalize(ass, offsetMs = 1_500)

        assertEquals(SubtitleNormalizer.Kind.SSA, r.kind)
        assertEquals(1, r.cues)
        assertTrue(r.text.contains("Dialogue: 0,0:00:02.50,0:00:04.00,Default,,0,0,0,,Hi"))
        assertTrue(r.text.contains("Style: Default,Arial,20"))
    }

    @Test
    fun `microdvd sub is converted rather than reported as junk`() {
        val sub = "{1}{1}25.000\n{25}{50}First line\n{75}{100}Second|Third\n"

        val r = SubtitleNormalizer.normalize(sub)

        assertEquals(SubtitleNormalizer.Kind.VTT, r.kind)
        assertEquals(2, r.cues)
        assertTrue(r.text.startsWith("WEBVTT\n\n"))
        assertTrue(r.text.contains("00:00:01.000 --> 00:00:02.000"))
        assertTrue(r.text.contains("00:00:03.000 --> 00:00:04.000"))
        assertTrue(r.text.contains("Second\nThird"))
    }

    @Test
    fun `microdvd styling tags are dropped instead of drawn`() {
        val sub = "{1}{1}25\n{25}{50}{y:i}Whispered\n"

        val r = SubtitleNormalizer.normalize(sub)

        assertEquals(1, r.cues)
        assertTrue(r.text.contains("Whispered"))
        assertFalse(r.text.contains("{y:i}"))
    }

    @Test
    fun `microdvd without a declared rate still yields cues`() {
        val sub = "{24}{48}No rate declared\n"

        val r = SubtitleNormalizer.normalize(sub)

        assertEquals(1, r.cues)
        assertTrue(r.text.contains("No rate declared"))
    }
}
