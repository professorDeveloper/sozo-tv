package com.saikou.sozo_tv.domain.player

import java.util.Locale

object SubtitleNormalizer {

    enum class Kind { VTT, SSA }

    /** [cues] of zero means the body is not a subtitle; the caller must skip the attach. */
    data class Result(val text: String, val kind: Kind, val cues: Int)

    private val CUE = Regex(
        """^\s*(?:\d+\s*)?(?:(\d{1,3}):)?(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(?:(\d{1,3}):)?(\d{2}):(\d{2})[,.](\d{1,3})(.*)$"""
    )

    private val MICRO_DVD = Regex("""^\{(\d+)\}\{(\d+)\}(.*)$""", RegexOption.MULTILINE)

    private val SSA_EVENT = Regex(
        """^(Dialogue:\s*[^,]*,)(\d{1,3}:\d{2}:\d{2}[.,]\d{1,3}),(\d{1,3}:\d{2}:\d{2}[.,]\d{1,3})(,.*)$"""
    )

    fun normalize(raw: String, offsetMs: Long = 0): Result {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace("\r", "\n")

        if (text.contains("[Script Info]") || text.contains("\nDialogue:")) {
            return ssa(text, offsetMs)
        }

        if (!text.contains("-->") && MICRO_DVD.containsMatchIn(text)) {
            return microDvd(text, offsetMs)
        }

        val lines = text.lines()
        val out = StringBuilder("WEBVTT\n\n")
        var cues = 0

        for ((i, line) in lines.withIndex()) {
            val m = CUE.matchEntire(line)
            if (m != null) {
                val (h1, m1, s1, ms1, h2, m2, s2, ms2, rest) = m.destructured
                out.append(stamp(millis(h1, m1, s1, ms1) + offsetMs))
                    .append(" --> ")
                    .append(stamp(millis(h2, m2, s2, ms2) + offsetMs))
                    .append(rest)
                    .append('\n')
                cues++
                continue
            }
            if (line.trim().toIntOrNull() != null && CUE.matches(lines.getOrNull(i + 1).orEmpty())) continue
            if (line.trim() == "WEBVTT" || line.startsWith("WEBVTT ")) continue
            if (line.contains("-->")) cues++
            out.append(line).append('\n')
        }

        return Result(out.toString(), Kind.VTT, cues)
    }

    private fun ssa(text: String, offsetMs: Long): Result {
        val lines = text.lineSequence()
        if (offsetMs == 0L) {
            return Result(text, Kind.SSA, lines.count { it.startsWith("Dialogue:") })
        }
        var cues = 0
        val out = StringBuilder()
        for (line in lines) {
            val m = SSA_EVENT.matchEntire(line)
            if (m == null) {
                out.append(line).append('\n')
                continue
            }
            val (head, start, end, tail) = m.destructured
            out.append(head)
                .append(ssaStamp(ssaMillis(start) + offsetMs))
                .append(',')
                .append(ssaStamp(ssaMillis(end) + offsetMs))
                .append(tail)
                .append('\n')
            cues++
        }
        return Result(out.toString(), Kind.SSA, cues)
    }

    private fun microDvd(text: String, offsetMs: Long): Result {
        val lines = text.lines()
        var fps = DEFAULT_FPS
        val out = StringBuilder("WEBVTT\n\n")
        var cues = 0

        for ((index, line) in lines.withIndex()) {
            val m = MICRO_DVD.matchEntire(line.trim()) ?: continue
            val (startFrame, endFrame, body) = m.destructured

            if (index == 0 || cues == 0) {
                val declared = body.trim().replace(',', '.').toDoubleOrNull()
                if (startFrame == endFrame && declared != null && declared in 1.0..1000.0) {
                    fps = declared
                    continue
                }
            }

            val start = (startFrame.toLong() * 1000.0 / fps).toLong() + offsetMs
            val end = (endFrame.toLong() * 1000.0 / fps).toLong() + offsetMs
            out.append(stamp(start)).append(" --> ").append(stamp(end)).append('\n')
            body.split('|').forEach { part ->
                out.append(part.replace(STYLE_TAG, "").removePrefix("/")).append('\n')
            }
            out.append('\n')
            cues++
        }

        return Result(out.toString(), Kind.VTT, cues)
    }

    private fun millis(h: String, m: String, s: String, ms: String): Long =
        (h.toLongOrNull() ?: 0L) * 3_600_000L +
                m.toLong() * 60_000L +
                s.toLong() * 1_000L +
                ms.padEnd(3, '0').toLong()

    private fun ssaMillis(stamp: String): Long {
        val parts = stamp.replace(',', '.').split(':', '.')
        if (parts.size != 4) return 0L
        return millis(parts[0], parts[1], parts[2], parts[3].padEnd(2, '0').take(2) + "0")
    }

    /** Locale.ROOT: an Arabic or Bengali locale would emit its own digits and WebVTT rejects them. */
    private fun stamp(totalMs: Long): String {
        val t = totalMs.coerceAtLeast(0L)
        return String.format(
            Locale.ROOT, "%02d:%02d:%02d.%03d",
            t / 3_600_000L, (t / 60_000L) % 60, (t / 1_000L) % 60, t % 1_000L
        )
    }

    private fun ssaStamp(totalMs: Long): String {
        val t = totalMs.coerceAtLeast(0L)
        return String.format(
            Locale.ROOT, "%d:%02d:%02d.%02d",
            t / 3_600_000L, (t / 60_000L) % 60, (t / 1_000L) % 60, (t % 1_000L) / 10
        )
    }

    private const val DEFAULT_FPS = 23.976
    private val STYLE_TAG = Regex("""\{[a-zA-Z]:[^}]*\}""")
}
