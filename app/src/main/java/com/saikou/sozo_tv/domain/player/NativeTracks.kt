package com.saikou.sozo_tv.domain.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.util.Locale

object NativeTracks {

    data class Option(
        val label: String,
        val detail: String,
        val group: Tracks.Group,
        val index: Int,
        val language: String = "",
        /** `Format.id` — the only thing that tells a sideloaded subtitle from a manifest one. */
        val id: String = "",
    )

    fun audio(tracks: Tracks): List<Option> = collect(tracks, C.TRACK_TYPE_AUDIO) { format ->
        languageOf(format) to audioDetail(format)
    }

    fun codeOf(format: Format): String {
        val code = format.language?.trim().orEmpty()
        return if (code.isEmpty() || code == "und") "" else code.lowercase()
    }

    fun text(tracks: Tracks): List<Option> =
        collect(tracks, C.TRACK_TYPE_TEXT, keepSingle = true) { format ->
            languageOf(format) to textDetail(format)
        }

    private fun collect(
        tracks: Tracks,
        type: Int,
        keepSingle: Boolean = false,
        describe: (Format) -> Pair<String, String>,
    ): List<Option> {
        val out = mutableListOf<Option>()
        for (group in tracks.groups) {
            if (group.type != type) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val (label, detail) = describe(format)
                out += Option(label, detail, group, i, codeOf(format), format.id.orEmpty())
            }
        }
        val distinct = out.distinctBy { Triple(it.language, it.label, it.detail) }
        val resolved = nameApart(distinct)
        return if (keepSingle || resolved.size > 1) resolved else emptyList()
    }

    private fun nameApart(options: List<Option>): List<Option> {
        val collides = options.groupingBy { it.label }.eachCount()
            .filterValues { it > 1 }.keys
        if (collides.isEmpty()) return options

        val named = options.map { o ->
            if (o.label !in collides) return@map o
            val language = displayName(o.language)
            if (language.isEmpty() || o.label.contains(language, ignoreCase = true)) o
            else o.copy(label = "${o.label} · $language")
        }

        val still = named.groupingBy { it.label }.eachCount().filterValues { it > 1 }.keys
        if (still.isEmpty()) return named
        val used = mutableMapOf<String, Int>()
        return named.map { o ->
            if (o.label !in still) return@map o
            val n = (used[o.label] ?: 0) + 1
            used[o.label] = n
            o.copy(label = "${o.label} $n")
        }
    }

    private fun languageOf(format: Format): String {
        format.label?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val code = format.language?.trim().orEmpty()
        if (code.isEmpty() || code == "und") return "Unknown"
        return displayName(code).ifEmpty { code.uppercase() }
    }

    private fun displayName(code: String): String {
        if (code.isEmpty() || code == "und") return ""
        val display = runCatching { Locale.forLanguageTag(code).displayLanguage }
            .getOrNull().orEmpty()
        if (display.isEmpty() || display.equals(code, ignoreCase = true)) return ""
        return display.replaceFirstChar { it.uppercase() }
    }

    private fun audioDetail(format: Format): String = buildList {
        when (format.channelCount) {
            1 -> add("Mono")
            2 -> add("Stereo")
            6 -> add("5.1")
            8 -> add("7.1")
            else -> if (format.channelCount > 0) add("${format.channelCount}ch")
        }
        codecName(format.codecs)?.let { add(it) }
        if (format.bitrate > 0) add("${format.bitrate / 1000} kbps")
        if (format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) add("Audio description")
    }.joinToString(" · ")

    private fun textDetail(format: Format): String = buildList {
        if (format.roleFlags and C.ROLE_FLAG_SUBTITLE != 0) add("Subtitle")
        if (format.roleFlags and C.ROLE_FLAG_CAPTION != 0) add("CC")
        if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) add("Forced")
        add("Embedded")
    }.joinToString(" · ")

    private fun codecName(codecs: String?): String? {
        val raw = codecs?.substringBefore('.')?.lowercase() ?: return null
        return when {
            raw.startsWith("mp4a") -> "AAC"
            raw.startsWith("ac-3") || raw.startsWith("ac3") -> "AC-3"
            raw.startsWith("ec-3") || raw.startsWith("ec3") -> "E-AC-3"
            raw.startsWith("opus") -> "Opus"
            raw.startsWith("vorbis") -> "Vorbis"
            raw.startsWith("dts") -> "DTS"
            raw.startsWith("flac") -> "FLAC"
            raw.isEmpty() -> null
            else -> raw.uppercase()
        }
    }

    fun overrideFor(option: Option): TrackSelectionOverride =
        TrackSelectionOverride(option.group.mediaTrackGroup, option.index)
}
