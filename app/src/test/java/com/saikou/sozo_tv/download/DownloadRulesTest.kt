package com.saikou.sozo_tv.download

import com.saikou.sozo_tv.engine.download.DownloadMetadata
import com.saikou.sozo_tv.engine.download.DownloadRules
import org.junit.Assert.*
import org.junit.Test

class DownloadRulesTest {
    @Test fun directSourcesAllowedButTemporaryAndProtectedSourcesRejected() {
        assertNull(DownloadRules.rejectReason("https://media.example/video.m3u8", null, false))
        assertNotNull(DownloadRules.rejectReason("http://127.0.0.1:8900/hls/session/file", null, false))
        assertNotNull(DownloadRules.rejectReason("magnet:?xt=urn:btih:123", null, false))
        assertNotNull(DownloadRules.rejectReason("https://media.example/video.mpd", "widevine", false))
        assertNotNull(DownloadRules.rejectReason("https://media.example/video.mp4", null, true))
        assertNotNull(DownloadRules.rejectReason("javascript:alert(1)", null, false))
    }

    @Test fun queuesAreScopedToHouseholdOwnerAndStableAcrossProcessRestart() {
        val first = DownloadRules.id("account-a:profile-a", "https://media.example/video.mp4")
        assertEquals(first, DownloadRules.id("account-a:profile-a", "https://media.example/video.mp4"))
        assertNotEquals(first, DownloadRules.id("account-b:profile-a", "https://media.example/video.mp4"))
        assertNotEquals(first, DownloadRules.id("account-a:profile-b", "https://media.example/video.mp4"))
    }

    @Test fun metadataPreservesHeadersAndTransformsForResume() {
        val metadata = DownloadMetadata("Episode 2 — Title", "account:profile", "https://media.example/video.m3u8?token=a%2Fb", "application/x-mpegURL",
            mapOf("Referer" to "https://embed.example/", "Cookie" to "session=value"), "{\"type\":\"proxy\"}", "{\"key\":\"test\"}")
        assertEquals(metadata, DownloadMetadata.decode(metadata.encode()))
        assertEquals(metadata.copy(localProxy = null, transform = null), DownloadMetadata.decode(metadata.copy(localProxy = null, transform = null).encode()))
    }
}
