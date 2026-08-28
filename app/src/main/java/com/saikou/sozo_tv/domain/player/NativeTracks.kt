package com.saikou.sozo_tv.domain.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.util.Locale

/**
 * The audio and subtitle tracks a stream already carries.
 *
 * The player has always had these — ExoPlayer parses every rendition in the
 * manifest — and the UI simply never asked. So a dual-audio release played
 * whichever track the preferred-language list happened to match, with no way to
 * say otherwise, and subtitles burned into the manifest were invisible next to
 * the ones the extractor supplied separately.
 *
 * Nothing here fetches anything. It reads what the player already knows and
 * turns it into something a person can choose from.
 */
object NativeTracks {

    data class Option(
        val label: String,
        val detail: String,
        val group: Tracks.Group,
        val index: Int,
        /** Raw BCP-47/ISO code, "" when the manifest declares none. Labels are a
         *  publisher's free text and differ between releases of the same show, so
         *  a remembered choice is matched on this instead. */
        val language: String = "",
        /** `Format.id`, "" when none was declared. The player merges a sideloaded
         *  subtitle FILE into the same track list as the manifest's own tracks, and
         *  the id is the only thing that tells the two apart — see
         *  `SeriesPlayerScreen.SIDELOADED_SUBTITLE_ID`. */
        val id: String = "",
    )

    /** Empty when there is nothing to choose between — one track is not a choice. */
    fun audio(tracks: Tracks): List<Option> = collect(tracks, C.TRACK_TYPE_AUDIO) { format ->
        languageOf(format) to audioDetail(format)
    }

    /** Normalised language code for [format], "" when it declares none. */
    fun codeOf(format: Format): String {
        val code = format.language?.trim().orEmpty()
        return if (code.isEmpty() || code == "und") "" else code.lowercase()
    }

    /**
     * Subtitle tracks inside the stream.
     *
     * Returned even when there is only one, unlike audio: a single embedded
     * subtitle track is still a choice, because the alternative is off.
     */
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
                // An unsupported track is one this device cannot decode. Offering
                // it means offering silence.
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val (label, detail) = describe(format)
                out += Option(label, detail, group, i, codeOf(format), format.id.orEmpty())
            }
        }
        // Same language twice with the same detail is a manifest artefact, not
        // two choices — but the language CODE has to be part of that judgement.
        // A dual-audio release routinely gives both renditions the identical
        // NAME ("Audio") and differs only by LANGUAGE, and keying on
        // (label, detail) alone folded those two into one, dropped below the
        // "more than one is a choice" line, and hid the audio menu entirely —
        // exactly the case the menu exists for.
        val distinct = out.distinctBy { Triple(it.language, it.label, it.detail) }
        val resolved = nameApart(distinct)
        return if (keepSingle || resolved.size > 1) resolved else emptyList()
    }

    /**
     * Gives colliding rows names a person can tell apart.
     *
     * Two rows both reading "Audio" is a menu nobody can use. The language is
     * what actually separates them, so it goes in the label; a number is the
     * last resort for a stream that declares nothing at all. Rows whose label is
     * already unique are left exactly as the publisher wrote them.
     */
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

        // Anything still sharing a name gets numbered, so every row is distinct
        // even when the manifest gave us nothing to distinguish them by.
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

    /**
     * A name for the track.
     *
     * The manifest's own label wins when it has one — it is what the publisher
     * chose to call it, and "Hindi 5.1 Commentary" beats anything derivable.
     * Otherwise the language code is turned into a real language name, because
     * "hin" is not something to put in front of a viewer.
     */
    private fun languageOf(format: Format): String {
        format.label?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val code = format.language?.trim().orEmpty()
        if (code.isEmpty() || code == "und") return "Unknown"
        return displayName(code).ifEmpty { code.uppercase() }
    }

    /** "ja" -> "Japanese"; "" when the code is missing or not a real language. */
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

    /** The bit of a codec string worth showing — "mp4a.40.2" tells a viewer nothing. */
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
