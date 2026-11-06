package com.saikou.sozo_tv.engine.player

/**
 * Takes the segments a source's `dropSegments` rule matches out of a media playlist, with their
 * timing. Port of the phone app's hls_segment_filter.dart.
 *
 * Some catalogues (kkphim) splice an ad into every stream as ordinary segments between two
 * discontinuities; only the path gives it away. The two discontinuities around it become one,
 * since the film's timestamps still jump there. A master playlist, or one the rule would empty,
 * comes back as it is.
 */
object HlsSegmentFilter {

    /** The server's rule, or null when there is none or it does not compile. */
    fun ruleFrom(pattern: String?): Regex? {
        if (pattern.isNullOrEmpty()) return null
        return runCatching { Regex(pattern) }.getOrNull()
    }

    fun drop(playlist: String, drop: Regex): String {
        if (playlist.contains("#EXT-X-STREAM-INF")) return playlist
        val out = mutableListOf<String>()
        // From an #EXTINF up to its uri, which decides whether they stay.
        val pending = mutableListOf<String>()
        var discontinuity = false
        var kept = 0
        var dropped = 0
        for (line in playlist.split('\n')) {
            val s = line.trim()
            when {
                s == "#EXT-X-DISCONTINUITY" -> discontinuity = true
                s.startsWith("#EXTINF") || pending.isNotEmpty() && s.startsWith("#") -> pending.add(line)
                s.isEmpty() || s.startsWith("#") -> out.add(line)
                drop.containsMatchIn(s) -> {
                    pending.clear()
                    dropped++
                }
                else -> {
                    if (discontinuity) out.add("#EXT-X-DISCONTINUITY")
                    discontinuity = false
                    out.addAll(pending)
                    out.add(line)
                    pending.clear()
                    kept++
                }
            }
        }
        if (dropped == 0 || kept == 0) return playlist
        return out.joinToString("\n")
    }
}
