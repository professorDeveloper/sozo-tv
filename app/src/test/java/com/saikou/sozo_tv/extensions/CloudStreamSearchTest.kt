package com.saikou.sozo_tv.extensions

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.saikou.sozo_tv.engine.cloudstream.searchCloudStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CloudStreamSearchTest {
    @Test fun `paged-only plugins receive first page and preserve hasNext`() = runBlocking {
        val api = object : MainAPI() {
            override suspend fun search(query: String, page: Int) =
                newSearchResponseList(emptyList(), true).also {
                    assertEquals("Naruto", query)
                    assertEquals(1, page)
                }
        }
        assertTrue(searchCloudStream(api, "Naruto")!!.hasNext)
    }

    @Test fun `legacy plugins remain supported`() = runBlocking {
        var called = false
        val api = object : MainAPI() {
            override suspend fun search(query: String): List<SearchResponse> {
                called = true
                return emptyList()
            }
        }
        assertFalse(searchCloudStream(api, "Naruto")!!.hasNext)
        assertTrue(called)
    }

    @Test fun `plugin failure does not become an empty success`() = runBlocking {
        val failure = NotImplementedError("unsupported plugin")
        val api = object : MainAPI() {
            override suspend fun search(query: String): List<SearchResponse> = throw failure
        }
        try {
            searchCloudStream(api, "Naruto")
            fail("Expected provider failure")
        } catch (actual: NotImplementedError) { assertSame(failure, actual) }
    }

    @Test fun `cancellation reaches caller`() = runBlocking {
        val failure = CancellationException("superseded")
        val api = object : MainAPI() {
            override suspend fun search(query: String): List<SearchResponse> = throw failure
        }
        try {
            searchCloudStream(api, "Naruto")
            fail("Expected cancellation")
        } catch (actual: CancellationException) { assertSame(failure, actual) }
    }
}
