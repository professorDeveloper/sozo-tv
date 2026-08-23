package com.saikou.sozo_tv.domain.player

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
            return Result(text, Kind.SSA, 0)
        }

        val lines = text.lines()
        val out = StringBuilder("WEBVTT\n\n")
        var cues = 0

        for ((i, line) in lines.withIndex()) {
            val m = CUE.matchEntire(line)
            if (m != null) {
                val (h1, m1, s1, ms1, h2, m2, s2, ms2, rest) = m.destructured
                out.append(
                    "%02d:%s:%s.%s --> %02d:%s:%s.%s%s\n".format(
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
            out.append(line).append('\n')
        }

        return Result(out.toString(), Kind.VTT, cues)
    }
}
