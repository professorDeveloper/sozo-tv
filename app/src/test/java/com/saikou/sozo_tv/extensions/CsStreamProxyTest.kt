package com.saikou.sozo_tv.extensions

import com.saikou.sozo_tv.engine.cloudstream.CsStreamProxy
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class CsStreamProxyTest {
    @Test fun `plugin interceptor handles playlist segments and subtitle-specific headers`() {
        val observed = java.util.Collections.synchronizedList(mutableListOf<Request>())
        val interceptor = Interceptor { chain ->
            observed.add(chain.request())
            val playlist = chain.request().url.encodedPath.endsWith(".m3u8")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((if (playlist) "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"../key\"\nsegment.ts\n" else "decrypted")
                    .toResponseBody((if (playlist) "application/vnd.apple.mpegurl" else "text/plain").toMediaType()))
                .build()
        }
        val token = CsStreamProxy.register(OkHttpClient(), interceptor, mapOf("Referer" to "https://watch/"))!!
        val local = OkHttpClient()
        val playlistUrl = CsStreamProxy.urlFor(token, "https://origin.test/path/master.m3u8")!!
        val playlist = local.newCall(Request.Builder().url(playlistUrl).build()).execute().use { it.body!!.string() }
        assertTrue(playlist.contains("http://127.0.0.1:"))
        assertFalse(playlist.contains("URI=\"../key\""))
        val segmentUrl = playlist.lineSequence().first { !it.startsWith("#") && it.isNotBlank() }
        local.newCall(Request.Builder().url(segmentUrl).header("Range", "bytes=4-9").build()).execute().use {
            assertEquals("decrypted", it.body!!.string())
        }
        val sub = CsStreamProxy.urlFor(token, "https://subs.test/en.txt", mapOf("Referer" to "https://subtitle/"))!!
        local.newCall(Request.Builder().url(sub).build()).execute().close()
        assertEquals("https://origin.test/path/segment.ts", observed[1].url.toString())
        assertEquals("bytes=4-9", observed[1].header("Range"))
        assertEquals("https://watch/", observed[1].header("Referer"))
        assertEquals("https://subtitle/", observed[2].header("Referer"))
    }
}
