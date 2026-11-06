package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.engine.player.HlsSegmentFilter
import org.junit.Assert.*
import org.junit.Test

class HlsSegmentFilterTest {
    // The shape of a live KKPhim playlist, 2026-10-31.
    private val playlist =
        "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-PLAYLIST-TYPE:vod\n" +
            "#EXT-X-TARGETDURATION:8\n#EXT-X-MEDIA-SEQUENCE:0\n" +
            "#EXTINF:4.0,\nHcTaGA8B.ts\n#EXT-X-DISCONTINUITY\n" +
            "#EXTINF:4.0,\nconvertv8/R9SjR8xq.ts\n#EXT-X-DISCONTINUITY\n" +
            "#EXTINF:4.0,\n8T4q13t8.ts\n#EXT-X-DISCONTINUITY\n" +
            "#EXTINF:3.0,\n/v8/d61a03b1dabe537cc05ffe5c9c280f56/segment_0000.ts\n" +
            "#EXTINF:2.0,\n/v8/d61a03b1dabe537cc05ffe5c9c280f56/segment_0001.ts\n" +
            "#EXT-X-DISCONTINUITY\n#EXTINF:4.0,\nZFE6Zblx.ts\n#EXT-X-ENDLIST\n"
    private val ad = HlsSegmentFilter.ruleFrom("^/v\\d+/[0-9a-f]{32}/")!!

    @Test fun takesTheAdAndItsTimingOutAndLeavesTheFilmWhole() {
        val out = HlsSegmentFilter.drop(playlist, ad)
        assertFalse(out.contains("segment_"))
        assertTrue(out.contains("convertv8/R9SjR8xq.ts"))
        assertEquals(4, Regex("#EXTINF").findAll(out).count())
        assertTrue(out.endsWith("ZFE6Zblx.ts\n#EXT-X-ENDLIST\n"))
    }

    @Test fun theTwoDiscontinuitiesAroundTheAdBecomeOne() {
        val out = HlsSegmentFilter.drop(playlist, ad)
        assertEquals(3, Regex("#EXT-X-DISCONTINUITY").findAll(out).count())
        assertTrue(out.contains("8T4q13t8.ts\n#EXT-X-DISCONTINUITY\n#EXTINF:4.0,\nZFE6Zblx.ts"))
    }

    @Test fun aMasterPlaylistIsNotAListOfSegments() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\n/v8/abc/index.m3u8\n"
        assertEquals(master, HlsSegmentFilter.drop(master, Regex(".")))
    }

    @Test fun aRuleThatWouldEmptyThePlaylistIsWrongNotTheFilm() {
        assertEquals(playlist, HlsSegmentFilter.drop(playlist, Regex(".")))
    }

    @Test fun aMissingOrBrokenRuleIsNoRule() {
        assertNull(HlsSegmentFilter.ruleFrom(null))
        assertNull(HlsSegmentFilter.ruleFrom(""))
        assertNull(HlsSegmentFilter.ruleFrom("(unclosed"))
    }
}
