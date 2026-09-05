package com.saikou.sozo_tv.extensions

import com.saikou.sozo_tv.engine.cloudstream.RepoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Community CloudStream repos are hand-maintained and their JSON reflects it: generators
 * leave nulls in `pluginLists`, entries lose a field, the same plugin is published in two
 * lists at once. Every one of those used to cost more than the entry it appeared in — at
 * worst the whole repo — and a repo that installs nothing reaches the viewer as the Sources
 * screen saying "No providers available", which is the same thing it says when no repo was
 * ever offered.
 */
class CloudStreamRepoParseTest {

    private val repoUrl = "https://example.invalid/repo.json"

    // ---------- repo.json ----------

    @Test
    fun `reads the repo name and its plugin lists`() {
        val info = RepoManager.parseRepoIndex(
            """{"name":"Phisher","pluginLists":["https://a/plugins.json","https://b/plugins.json"]}""",
            repoUrl,
        )
        assertEquals("Phisher", info.name)
        assertEquals(listOf("https://a/plugins.json", "https://b/plugins.json"), info.pluginListUrls)
    }

    @Test
    fun `a body that is already a plugins array is treated as its own plugin list`() {
        val info = RepoManager.parseRepoIndex("""  [{"url":"https://a/x.cs3"}]""", repoUrl)
        assertEquals(null, info.name)
        assertEquals(listOf(repoUrl), info.pluginListUrls)
    }

    @Test
    fun `one unusable pluginLists entry costs that entry, not the repo`() {
        // getString threw on the null, the throw escaped the whole parse, and the repo
        // installed nothing at all — every other list in it went unread.
        val info = RepoManager.parseRepoIndex(
            """{"name":"R","pluginLists":["https://a/plugins.json",null,"","https://b/plugins.json"]}""",
            repoUrl,
        )
        assertEquals(listOf("https://a/plugins.json", "https://b/plugins.json"), info.pluginListUrls)
    }

    @Test
    fun `a repo with no name or no plugin lists parses to empty rather than throwing`() {
        assertEquals(null, RepoManager.parseRepoIndex("""{"pluginLists":[]}""", repoUrl).name)
        assertTrue(RepoManager.parseRepoIndex("""{"name":"R"}""", repoUrl).pluginListUrls.isEmpty())
        assertTrue(RepoManager.parseRepoIndex("not json", repoUrl).pluginListUrls.isEmpty())
        assertTrue(RepoManager.parseRepoIndex("", repoUrl).pluginListUrls.isEmpty())
    }

    // ---------- plugins.json ----------

    private fun plugin(
        name: String, version: Int = 1, status: Int? = null, url: String = "https://a/$name.cs3",
    ): String {
        val statusField = if (status == null) "" else ""","status":$status"""
        return """{"internalName":"$name","url":"$url","version":$version$statusField}"""
    }

    @Test
    fun `reads every field an install needs`() {
        val refs = RepoManager.parsePluginList(
            """[{"internalName":"Netflix","url":"https://a/n.cs3","version":7,"iconUrl":"https://a/n.png"}]"""
        )
        val ref = refs.single()
        assertEquals("Netflix", ref.internalName)
        assertEquals("https://a/n.cs3", ref.url)
        assertEquals(7, ref.version)
        assertEquals("https://a/n.png", ref.iconUrl)
    }

    @Test
    fun `an entry with no internalName falls back to its display name`() {
        val ref = RepoManager.parsePluginList("""[{"name":"Prime Video","url":"https://a/p.cs3"}]""").single()
        assertEquals("Prime Video", ref.internalName)
    }

    @Test
    fun `an entry with no version installs as version zero`() {
        assertEquals(0, RepoManager.parsePluginList("""[{"internalName":"A","url":"https://a/a.cs3"}]""").single().version)
    }

    @Test
    fun `an entry with no url is dropped, and only that entry`() {
        // There is nothing to download; keeping it would show a source that never answers.
        val refs = RepoManager.parsePluginList(
            """[{"internalName":"A"},{"internalName":"B","url":""},${plugin("C")}]"""
        )
        assertEquals(listOf("C"), refs.map { it.internalName })
    }

    @Test
    fun `an entry the repo marks as down is dropped and one with no status is kept`() {
        val refs = RepoManager.parsePluginList(
            "[${plugin("Down", status = 0)},${plugin("Ok", status = 1)}," +
                "${plugin("Slow", status = 2)},${plugin("NoField")}]"
        )
        assertEquals(listOf("Ok", "Slow", "NoField"), refs.map { it.internalName })
    }

    @Test
    fun `a non-object entry is skipped without taking the list with it`() {
        val refs = RepoManager.parsePluginList("""[null,3,"x",${plugin("Real")}]""")
        assertEquals(listOf("Real"), refs.map { it.internalName })
    }

    @Test
    fun `a body that is not an array yields nothing rather than throwing`() {
        assertTrue(RepoManager.parsePluginList("").isEmpty())
        assertTrue(RepoManager.parsePluginList("<html>rate limited</html>").isEmpty())
        assertTrue(RepoManager.parsePluginList("""{"plugins":[]}""").isEmpty())
    }

    // ---------- across plugin lists ----------

    @Test
    fun `a plugin published in two lists is installed once, at its highest version`() {
        val refs = RepoManager.dedupePlugins(
            RepoManager.parsePluginList("[${plugin("A", version = 3)},${plugin("B", version = 1)}]") +
                RepoManager.parsePluginList("[${plugin("A", version = 5, url = "https://b/A.cs3")}]")
        )
        assertEquals(listOf("A", "B"), refs.map { it.internalName })
        assertEquals(5, refs.first().version)
        assertEquals("https://b/A.cs3", refs.first().url)
    }

    @Test
    fun `an older duplicate never displaces the newer one already seen`() {
        val refs = RepoManager.dedupePlugins(
            RepoManager.parsePluginList("[${plugin("A", version = 9)}]") +
                RepoManager.parsePluginList("[${plugin("A", version = 2)}]")
        )
        assertEquals(9, refs.single().version)
    }

    @Test
    fun `first-seen order survives deduplication`() {
        // The order plugins load in is the order their providers reach the Sources list.
        val refs = RepoManager.dedupePlugins(
            RepoManager.parsePluginList(
                "[${plugin("C")},${plugin("A")},${plugin("B")},${plugin("A", version = 4)}]"
            )
        )
        assertEquals(listOf("C", "A", "B"), refs.map { it.internalName })
    }
}
