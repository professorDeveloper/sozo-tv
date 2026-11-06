package com.saikou.sozo_tv.engine.server

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MediaLanguagesTest {
    private fun payload(url: String, sub: String?, headers: JSONObject = JSONObject()) = JSONObject()
        .put("videoUrl", url)
        .put("headers", headers)
        .put("videoSources", JSONArray().put(JSONObject().put("quality", "1080p").put("videoUrl", url).put("isDefault", true)))
        .put("subtitles", JSONArray().apply {
            if (sub != null) put(JSONObject().put("label", "English").put("file", sub).put("default", true))
        })
        .toString()

    @Test fun subComesFirstWhateverTheProviderOrder() {
        val e = JSONObject().put("availableLangs", JSONArray().put("dub").put("sub").put("dub"))
        assertEquals(listOf("sub", "dub"), MediaLanguages.of(e))
    }

    @Test fun qualitiesListedAsLanguagesAreNotLanguages() {
        val e = JSONObject().put("availableLangs", JSONArray().put("720p").put("1080p").put("HD"))
        assertEquals(emptyList<String>(), MediaLanguages.of(e))
    }

    @Test fun eachLanguageBecomesItsOwnServer() {
        val merged = JSONObject(MediaLanguages.merge(listOf(
            "Sub" to payload("https://a/sub.m3u8", "https://a/en.vtt", JSONObject().put("Referer", "https://a/")),
            "Dub" to payload("https://b/dub.m3u8", "https://b/en.vtt", JSONObject().put("Referer", "https://b/")),
        )))
        val sources = merged.getJSONArray("videoSources")
        assertEquals(2, sources.length())
        assertEquals("Sub", sources.getJSONObject(0).getString("host"))
        assertEquals("Dub", sources.getJSONObject(1).getString("host"))
        assertFalse(sources.getJSONObject(1).getBoolean("isDefault"))
        // The dub keeps its own Referer even though the media level is the sub's.
        assertEquals("https://b/", sources.getJSONObject(1).getJSONObject("headers").getString("Referer"))
        assertEquals("https://a/sub.m3u8", merged.getString("videoUrl"))
        assertFalse(merged.getJSONArray("subtitles").getJSONObject(1).getBoolean("default"))
    }
}
