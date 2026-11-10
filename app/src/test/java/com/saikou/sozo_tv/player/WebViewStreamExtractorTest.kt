package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.engine.player.WebViewStreamExtractor
import org.junit.Assert.*
import org.junit.Test

class WebViewStreamExtractorTest {
    @Test fun extensionlessEmbedRequiresExtraction() {
        assertTrue(WebViewStreamExtractor.needsExtraction("https://luluvdoo.com/e/u8f0iid8h1ze"))
    }
    @Test fun directMediaNeverRequiresExtraction() {
        assertFalse(WebViewStreamExtractor.needsExtraction("https://cdn.test/e/movie.mp4?token=x"))
    }
}
