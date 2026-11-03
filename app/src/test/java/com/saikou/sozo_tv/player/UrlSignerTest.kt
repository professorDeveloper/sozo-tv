package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.engine.player.UrlSigner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

// A file the device signs itself: asked with the player's own User-Agent, read out of the host's
// JSON map, and not asked twice.
class UrlSignerTest {
    private val asked = mutableListOf<Request>()

    // Answers like fayllar-sign.php, without a network.
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val req = chain.request()
        asked.add(req)
        val file = req.url.queryParameter("u[]").orEmpty()
        val body = if (file.isEmpty()) "{}"
            else JSONObject().put(file, "$file?token=abc&ua=${req.header("User-Agent")}").toString()
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()

    private fun signer() = UrlSigner.fromJson(
        """{"url":"https://asilmedia.org/fayllar-sign.php","param":"u[]","headers":{"Referer":"https://asilmedia.org/"}}"""
    )!!

    @Before fun reset() = UrlSigner.clearCache()

    @Test fun signsWithThePlayersOwnUserAgentAndTheHostsHeaders() {
        val signed = signer().sign("https://fayllar1.ru/9/Film%20720p%20O'zbek.mp4",
            mapOf("user-agent" to "PlayerUA/1"), client)
        assertEquals("https://fayllar1.ru/9/Film 720p O'zbek.mp4?token=abc&ua=PlayerUA/1", signed)
        // Sent decoded, as the page itself sends it.
        assertEquals("https://fayllar1.ru/9/Film 720p O'zbek.mp4", asked.single().url.queryParameter("u[]"))
        assertEquals("https://asilmedia.org/", asked.single().header("Referer"))
    }

    @Test fun asksOncePerFileAndUserAgent() {
        val s = signer()
        s.sign("https://fayllar1.ru/9/a.mp4", mapOf("User-Agent" to "X"), client)
        s.sign("https://fayllar1.ru/9/a.mp4", mapOf("User-Agent" to "X"), client)
        assertEquals(1, asked.size)
        s.sign("https://fayllar1.ru/9/a.mp4", mapOf("User-Agent" to "Y"), client)
        assertEquals(2, asked.size)
    }

    @Test fun noSignerWithoutAUrlAndAParameter() {
        assertNull(UrlSigner.fromJson("{}"))
        assertNull(UrlSigner.fromJson("""{"url":"https://x"}"""))
        assertNull(UrlSigner.fromJson(null))
    }

    @Test fun refusesAnAnswerThatIsNotAUrl() {
        val refusing = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"x":"denied"}""".toResponseBody(null)).build()
        }.build()
        assertNull(signer().sign("https://fayllar1.ru/9/b.mp4", emptyMap(), refusing))
    }
}
