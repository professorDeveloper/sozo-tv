package com.saikou.sozo_tv.link

import com.saikou.sozo_tv.data.extensions.ExtGroup
import com.saikou.sozo_tv.engine.link.ExtensionLinkRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * The phone hands this endpoint a string and the TV downloads and loads code from wherever
 * it points. Everything below is either a paste that used to fail silently, or a value that
 * must never be accepted at all.
 */
class ExtensionLinkRulesTest {

    // ---------- pairing code ----------

    @Test
    fun `the code leaves out the characters that get misread across a room`() {
        val alphabet = ExtensionLinkRules.CODE_ALPHABET
        for (c in listOf('I', 'O', '0', '1')) {
            assertFalse("'$c' is unreadable on a TV at 3 metres", alphabet.contains(c))
        }
        assertEquals(6, ExtensionLinkRules.newCode(Random(7)).length)
    }

    @Test
    fun `a code is matched the way a person types it back`() {
        assertTrue(ExtensionLinkRules.codeMatches("ABC234", "abc234"))
        assertTrue(ExtensionLinkRules.codeMatches("ABC234", " ABC 234 "))
        // Shown as ABC-234 on screen, so that is what gets typed.
        assertTrue(ExtensionLinkRules.codeMatches("ABC234", "ABC-234"))
        assertFalse(ExtensionLinkRules.codeMatches("ABC234", "ABC235"))
        assertFalse(ExtensionLinkRules.codeMatches("ABC234", null))
        assertFalse(ExtensionLinkRules.codeMatches("ABC234", ""))
    }

    @Test
    fun `an empty expected code never matches anything`() {
        // Otherwise a server that has not minted a code yet would accept a blank one.
        assertFalse(ExtensionLinkRules.codeMatches("", ""))
        assertFalse(ExtensionLinkRules.codeMatches("", "anything"))
    }

    // ---------- what people actually paste ----------

    @Test
    fun `a github page url is rewritten to the file it shows`() {
        // This is what copying out of a browser gives you. Fetched as-is it returns HTML,
        // the installer parses no plugins, and the repo reports that it added nothing.
        assertEquals(
            "https://raw.githubusercontent.com/phisher98/cs-ext/builds/repo.json",
            ExtensionLinkRules.normalizeRepoUrl(
                "https://github.com/phisher98/cs-ext/blob/builds/repo.json"
            ),
        )
        assertEquals(
            "https://raw.githubusercontent.com/a/b/main/repo.json",
            ExtensionLinkRules.normalizeRepoUrl("https://github.com/a/b/raw/main/repo.json"),
        )
    }

    @Test
    fun `a url that is already raw is left alone`() {
        val raw = "https://raw.githubusercontent.com/a/b/builds/repo.json"
        assertEquals(raw, ExtensionLinkRules.normalizeRepoUrl(raw))
    }

    @Test
    fun `a github url that is not a file link is left alone`() {
        // The repo landing page. Nothing to rewrite it to, and guessing a branch would be
        // worse than handing it on and letting the installer report what it found.
        val page = "https://github.com/phisher98/cs-ext"
        assertEquals(page, ExtensionLinkRules.normalizeRepoUrl(page))
    }

    @Test
    fun `a shortcode resolves to its curated repo`() {
        val url = ExtensionLinkRules.normalizeRepoUrl("phisher", ExtGroup.CLOUDSTREAM)
        assertTrue(url!!.startsWith("https://raw.githubusercontent.com/phisher98/"))
        assertEquals(url, ExtensionLinkRules.normalizeRepoUrl("  PHISHER  ", ExtGroup.CLOUDSTREAM))
    }

    @Test
    fun `quotes brackets and stray whitespace survive a paste from chat`() {
        assertEquals(
            "https://example.invalid/repo.json",
            ExtensionLinkRules.normalizeRepoUrl("  <https://example.invalid/repo.json>  "),
        )
        assertEquals(
            "https://example.invalid/repo.json",
            ExtensionLinkRules.normalizeRepoUrl("\"https://example.invalid/ repo.json\""),
        )
    }

    @Test
    fun `a bare host is assumed to be https, never http`() {
        assertEquals(
            "https://example.invalid/repo.json",
            ExtensionLinkRules.normalizeRepoUrl("example.invalid/repo.json"),
        )
    }

    // ---------- what must never be accepted ----------

    @Test
    fun `only http and https reach the installer`() {
        // This value is handed to code that downloads and loads a plugin. The accepted
        // schemes are a closed list; anything else is refused rather than filtered.
        for (bad in listOf(
            "file:///data/data/com.saikou.sozo_tv/repo.json",
            "javascript:alert(1)",
            "data:application/json,[]",
            "content://com.android.providers/x",
            "ftp://example.invalid/repo.json",
        )) {
            assertNull(bad, ExtensionLinkRules.normalizeRepoUrl(bad))
        }
    }

    @Test
    fun `a typo does not become a url`() {
        assertNull(ExtensionLinkRules.normalizeRepoUrl("repo json please"))
        assertNull(ExtensionLinkRules.normalizeRepoUrl(""))
        assertNull(ExtensionLinkRules.normalizeRepoUrl(null))
        assertNull(ExtensionLinkRules.normalizeRepoUrl("   "))
        assertNull(ExtensionLinkRules.normalizeRepoUrl(".json"))
    }

    // ---------- which engine ----------

    @Test
    fun `aniyomi is recognised by its index file, cloudstream is the fallback`() {
        assertEquals(
            ExtGroup.ANIYOMI,
            ExtensionLinkRules.guessGroup("https://x/anime-repo/repo/index.min.json"),
        )
        // CloudStream repos are not always called repo.json — two in the curated list are
        // not — so it is the fallback for any .json rather than a second pattern.
        assertEquals(ExtGroup.CLOUDSTREAM, ExtensionLinkRules.guessGroup("https://x/repo.json"))
        assertEquals(ExtGroup.CLOUDSTREAM, ExtensionLinkRules.guessGroup("https://x/Netflix.json"))
        assertNull(ExtensionLinkRules.guessGroup("https://github.com/a/b"))
    }

    // ---------- the address printed under the QR ----------

    @Test
    fun `an address a phone cannot reach is never shown`() {
        // Loopback is what the HLS proxy binds to; 169.254 means DHCP never finished. Both
        // look like ordinary addresses in the interface list and both lead to a page that
        // will not load.
        assertFalse(ExtensionLinkRules.isReachableLanAddress("127.0.0.1"))
        assertFalse(ExtensionLinkRules.isReachableLanAddress("169.254.3.9"))
        assertFalse(ExtensionLinkRules.isReachableLanAddress("0.0.0.0"))
        assertFalse(ExtensionLinkRules.isReachableLanAddress("fe80::1"))
        assertFalse(ExtensionLinkRules.isReachableLanAddress(null))
        assertFalse(ExtensionLinkRules.isReachableLanAddress(""))
        assertTrue(ExtensionLinkRules.isReachableLanAddress("192.168.1.42"))
        assertTrue(ExtensionLinkRules.isReachableLanAddress("10.0.0.7"))
    }

    @Test
    fun `the printed url carries the port`() {
        assertEquals("http://192.168.1.42:8787", ExtensionLinkRules.lanUrl("192.168.1.42", 8787))
    }
}
