package com.saikou.sozo_tv.extensions

import com.saikou.sozo_tv.data.extensions.ExtParser
import com.saikou.sozo_tv.engine.cloudstream.CloudStreamLinkQuality
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudStreamMediaTest {
    @Test fun `unknown adaptive ranks between 720 and 1080 without claiming 400p`() {
        assertFalse(CloudStreamLinkQuality.isKnown(400))
        assertTrue(CloudStreamLinkQuality.rank(400, "hls") > CloudStreamLinkQuality.rank(720, "http"))
        assertTrue(CloudStreamLinkQuality.rank(400, "dash") < CloudStreamLinkQuality.rank(1080, "http"))
        assertEquals(0, CloudStreamLinkQuality.rank(400, "http"))
    }
    @Test fun `DRM and auxiliary request headers survive media parsing`() {
        val media = ExtParser.media("""{"videoSources":[{"videoUrl":"https://cdn/video.mpd","type":"dash","drm":{"scheme":"widevine","licenseUrl":"https://license/","licenseHeaders":{"Authorization":"test"}},"audioTracks":[{"url":"https://audio/","headers":{"Origin":"https://site"}}]}],"subtitles":[{"file":"https://sub/","headers":{"Referer":"https://page"}}]}""")
        val drm = JSONObject(media.sources.single().drm!!)
        assertEquals("widevine", drm.getString("scheme"))
        assertEquals("test", drm.getJSONObject("licenseHeaders").getString("Authorization"))
        assertEquals("https://site", media.sources.single().audioTracks.single().headers["Origin"])
        assertEquals("https://page", media.subtitles.single().headers["Referer"])
    }
    @Test fun `empty failed provider remains an error rather than successful empty media`() {
        try { ExtParser.media("""{"videoSources":[],"error":"Provider: timed out"}"""); fail("expected failure") }
        catch (e: IllegalStateException) { assertEquals("Provider: timed out", e.message) }
    }
    @Test fun `a partial usable result is retained despite one extractor failing`() {
        assertEquals(1, ExtParser.media("""{"videoSources":[{"videoUrl":"https://cdn/video.mp4"}],"error":"mirror failed"}""").sources.size)
    }
}
