package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.domain.player.ExternalPlaybackRules as Rules
import org.junit.Assert.*
import org.junit.Test

class ExternalPlaybackRulesTest {
    @Test fun systemTvPlaceholderIsNotAPlayer() {
        assertFalse(Rules.isPlayerPackage("com.android.tv.frameworkpackagestubs"))
        assertFalse(Rules.isPlayerPackage(""))
        assertTrue(Rules.isPlayerPackage("org.videolan.vlc"))
    }
    @Test fun directMediaCanBeHandedOff() { assertTrue(Rules.canOpen("https://cdn.test/movie.m3u8", null, false)) }
    @Test fun localSessionsProxyAndDrmStayInNativePlayer() {
        assertFalse(Rules.canOpen("https://cdn.test/movie.mpd", "{drm}", false))
        assertFalse(Rules.canOpen("https://cdn.test/movie.m3u8", null, true))
        listOf("http://127.0.0.1:4000/hls/id/x", "http://localhost/play", "http://[::1]/play", "magnet:?xt=urn", "file:///private/movie.mp4")
            .forEach { assertFalse(it, Rules.canOpen(it, null, false)) }
    }
    @Test fun headerPairsRetainNamesAndValues() {
        assertArrayEquals(arrayOf("Referer", "https://embed/", "X-Token", "once"),
            Rules.headerArray(linkedMapOf("Referer" to "https://embed/", "X-Token" to "once")))
    }
}
