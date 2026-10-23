package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.engine.player.StreamRequestRules as Rules
import org.junit.Assert.*
import org.junit.Test

class StreamRequestRulesTest {
    @Test fun nestedFrameHeadersOverrideOuterCaseInsensitively() {
        val result = Rules.headers(mapOf("referer" to "https://outer/", "User-Agent" to "ua"),
            mapOf("Referer" to "https://inner/frame", "Origin" to "https://inner", "Range" to "bytes=0-"),
            "https://inner/frame", "default", "session=ok")
        assertEquals("https://inner/frame", result["Referer"])
        assertEquals(1, result.keys.count { it.equals("referer", true) })
        assertFalse(result.containsKey("Range"))
        assertEquals("session=ok", result["Cookie"])
    }
    @Test fun dashIsNotHls() { assertEquals("dash", Rules.playType("https://cdn/a.mpd?sig=x")) }
    @Test fun relayOnlyTargetsKnownWrappedSegments() {
        val segment = "https://p16-ad-site-sign-sg.tiktokcdn.com/ad-site-i18n-sg/test.image?sig=a%2Bb"
        assertTrue(Rules.segmentRelay(segment, "https://api/relay")!!.contains("sig%3Da%252Bb"))
        assertNull(Rules.segmentRelay(segment.replace(".com/", ".com.evil/"), "https://api/relay"))
        assertNull(Rules.segmentRelay(segment.replace("https:", "http:"), "https://api/relay"))
        assertNull(Rules.segmentRelay(segment.replace(".com/", ".com:444/"), "https://api/relay"))
    }
    @Test fun preserveCrossHostSchemeSignedQueryAndRootPaths() {
        assertEquals("http://other/a.ts?sig=1", Rules.resolve("https://origin/base/list.m3u8?token=x", "http://other/a.ts?sig=1"))
        assertEquals("https://origin/a.ts", Rules.resolve("https://origin/base/list.m3u8?token=x", "/a.ts"))
        assertEquals("https://origin/a%20b.ts", Rules.resolve("https://origin/base/list.m3u8", "../a b.ts"))
        assertEquals("https://other/a.ts", Rules.resolve("https://origin/base/list.m3u8", "//other/a.ts"))
    }
    @Test fun manifestPatternFollowsRotatingCdnDomains() {
        assertTrue(Rules.needsManifestProxy("https://new-cdn.example/stream/a/master.m3u8?token=x"))
        assertFalse(Rules.needsManifestProxy("https://new-cdn.example/video/master.m3u8"))
    }
}
