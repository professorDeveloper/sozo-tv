package com.saikou.sozo_tv.domain.player

import java.util.Locale

/**
 * Turns whatever a source calls a "subtitle" into something the player can actually parse.
 *
 * Sources hand back .srt, .ass and half-broken .vtt behind the same url. Declaring all of it
 * as WebVTT and prepending a `WEBVTT` header made the parser reject every cue whose timestamp
 * used a comma — the track existed, was selectable, and never drew a single line.
 *
 * Kept out of the player fragment so the conversion can be tested on its own.
 */
object SubtitleNormalizer {

    enum class Kind { VTT, SSA }

    /**
     * [cues] counts the timing lines the output actually contains — the ones this converted
     * plus the ones it passed through untouched. **Zero means the body is not a subtitle.**
     *
     * That is not a theoretical case: a subtitle CDN that rejects the request routinely answers
     * `200` with an HTML error page, and a truncated download ends mid-file. Both used to be
     * wrapped in a `WEBVTT` header, written to disk and handed to the player, which then
     * selected a track containing no cues and drew nothing — indistinguishable, from the sofa,
     * from "subtitles are broken". The caller is expected to check this and skip the attach.
     */
    data class Result(val text: String, val kind: Kind, val cues: Int)

    /**
     * One cue's timing line, in every shape seen in the wild:
     *  - `00:00:06,376 --> 00:00:46,876`   SRT commas
     *  - `512` glued to the front when the source omits the newline after the index
     *  - one- or two-digit hours, one- to three-digit milliseconds
     */
    private val CUE = Regex(
        """^\s*(?:\d+\s*)?(\d{1,3}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,3}):(\d{2}):(\d{2})[,.](\d{1,3})(.*)$"""
    )

    fun normalize(raw: String): Result {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace("\r", "\n")

        if (text.contains("[Script Info]") || text.contains("\nDialogue:")) {
            return Result(text, Kind.SSA, text.lineSequence().count { it.startsWith("Dialogue:") })
        }

        val lines = text.lines()
        val out = StringBuilder("WEBVTT\n\n")
        var cues = 0

        for ((i, line) in lines.withIndex()) {
            val m = CUE.matchEntire(line)
            if (m != null) {
                val (h1, m1, s1, ms1, h2, m2, s2, ms2, rest) = m.destructured
                out.append(
                    // Locale.ROOT, not the device's: `%02d` under an Arabic, Persian or Bengali
                    // locale emits that locale's own digits ("٠٠"), which WebVTT does not accept,
                    // so every cue in the file was silently dropped on exactly the devices whose
                    // users need subtitles most.
                    String.format(
                        Locale.ROOT,
                        "%02d:%s:%s.%s --> %02d:%s:%s.%s%s\n",
                        h1.toInt(), m1, s1, ms1.padEnd(3, '0'),
                        h2.toInt(), m2, s2, ms2.padEnd(3, '0'), rest
                    )
                )
                cues++
                continue
            }
            // A bare number immediately before a timing line is an SRT index, not a cue id.
            if (line.trim().toIntOrNull() != null && CUE.matches(lines.getOrNull(i + 1).orEmpty())) continue
            if (line.trim() == "WEBVTT" || line.startsWith("WEBVTT ")) continue
            // A timing line this pass could not rewrite (WebVTT's hour-less `MM:SS.mmm` form,
            // for one) is still a cue and goes out verbatim — but it has to be counted, or a
            // perfectly good file would report zero cues and be thrown away as junk.
            if (line.contains("-->")) cues++
            out.append(line).append('\n')
        }

        return Result(out.toString(), Kind.VTT, cues)
    }
}
