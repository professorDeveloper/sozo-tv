package com.saikou.sozo_tv.subtitles

import com.google.gson.Gson
import com.saikou.sozo_tv.data.remote.subtitles.OnlineSubtitle
import com.saikou.sozo_tv.data.remote.subtitles.SubtitleSearchClient
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSearchParseTest {
    private val client = SubtitleSearchClient(
        okHttpClient = OkHttpClient(),
        gson = Gson(),
        baseUrl = "https://example.invalid",
    )

    private fun parse(raw: String): List<OnlineSubtitle> = client.parseItems(raw)

    @Test
    fun `reads the file url under either name`() {
        val fromFile = parse("""{"items":[{"file":"https://a/x.srt","language":"en"}]}""")
        val fromUrl = parse("""{"items":[{"url":"https://a/x.srt","language":"en"}]}""")
        assertEquals("https://a/x.srt", fromFile.single().url)
        assertEquals("https://a/x.srt", fromUrl.single().url)
    }

    @Test
    fun `reads the language under either name and uppercases it`() {
        assertEquals("EN", parse("""{"items":[{"file":"u","language":"en"}]}""").single().language)
        assertEquals("UZ", parse("""{"items":[{"file":"u","lang":"uz"}]}""").single().language)
    }

    @Test
    fun `a row with no file url is dropped`() {
        val items = parse("""{"items":[{"language":"en"},{"file":"","lang":"ru"},{"file":"u","lang":"tr"}]}""")
        assertEquals(1, items.size)
        assertEquals("TR", items.single().language)
    }

    @Test
    fun `malformed and empty payloads give an empty list rather than throwing`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("not json").isEmpty())
        assertTrue(parse("""{"items":null}""").isEmpty())
        assertTrue(parse("""{"items":[]}""").isEmpty())
        assertTrue(parse("""{"error":"nope"}""").isEmpty())
        assertTrue(parse("""{"items":[null,3,"x"]}""").isEmpty())
    }

    @Test
    fun `hearingImpaired survives a non-boolean`() {
        assertTrue(parse("""{"items":[{"file":"u","hearingImpaired":true}]}""").single().hearingImpaired)
        assertTrue(!parse("""{"items":[{"file":"u"}]}""").single().hearingImpaired)
        assertTrue(!parse("""{"items":[{"file":"u","hearingImpaired":"maybe"}]}""").single().hearingImpaired)
    }

    @Test
    fun `the detail line names the language, format and CC marker`() {
        val item = parse(
            """{"items":[{"file":"u","language":"en","label":"English","format":"srt","hearingImpaired":true}]}"""
        ).single()
        assertEquals("EN · English · srt · CC", item.detailLabel)
    }

    @Test
    fun `the detail line does not repeat the language as its own label`() {
        val item = parse("""{"items":[{"file":"u","language":"en","label":"EN"}]}""").single()
        assertEquals("EN", item.detailLabel)
    }

    @Test
    fun `parses the shape the live endpoint actually sends`() {
        val items = parse(
            """{"items":[
              {"label":"English","file":"https://s/1.srt","language":"eng","kind":"subtitles","default":true},
              {"label":"pob","file":"https://s/2.srt","language":"pob","kind":"subtitles","default":false},
              {"label":"Turkish","file":"https://s/3.srt","language":"tur","kind":"subtitles","default":false}
            ]}"""
        )
        assertEquals(3, items.size)
        assertEquals("ENG", items[0].language)
        assertEquals("English", items[0].display)
        assertEquals("ENG · English", items[0].detailLabel)
        assertEquals("POB", items[1].detailLabel)
        assertEquals("https://s/3.srt", items[2].url)
    }

    @Test
    fun `the primary label prefers the filename, which is what tells releases apart`() {
        val named = parse(
            """{"items":[{"file":"u","language":"en","fileName":"Movie.2019.1080p.WEB.srt"}]}"""
        ).single()
        assertEquals("Movie.2019.1080p.WEB.srt", named.primaryLabel)

        val unnamed = parse("""{"items":[{"file":"u","language":"en","label":"English"}]}""").single()
        assertEquals("English", unnamed.primaryLabel)
    }
}
