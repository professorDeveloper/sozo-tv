package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.domain.player.SubtitleNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The samples here are the shapes that actually reached the player and produced
 * hundreds of "Skipping cue with bad header" lines, copied from a device log.
 */
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
        // No comma may survive inside a timing line, or the cue is dropped again.
        assertFalse(r.text.lines().any { it.contains("-->") && it.contains(",") })
        assertTrue(r.text.contains("Hello there"))
    }

    @Test
    fun `index glued to the timestamp is still parsed`() {
        // Real line from the log: the source omitted the newline after index 512.
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

        // "7" indexed the cue; "42" is the actual line of dialogue and must survive.
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
    }
}
