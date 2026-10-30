package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.engine.player.DashManifestRewriter
import org.junit.Assert.*
import org.junit.Test

class DashManifestRewriterTest {
    @Test fun templatesResolveHierarchyAndKeepPlayerTokensVisible() {
        val xml = """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"><BaseURL>https://cdn.test/media/</BaseURL><Period><AdaptationSet>
            <SegmentTemplate media="${'$'}RepresentationID${'$'}/seg-${'$'}Number%05d${'$'}.m4s" initialization="init.mp4"/>
            <Representation id="v"><BaseURL>video/</BaseURL></Representation>
            </AdaptationSet></Period></MPD>"""
        val urls = mutableListOf<String>()
        val out = DashManifestRewriter.rewrite(xml, "https://origin.test/manifest.mpd") { urls.add(it); "https://proxy.test/?source=$it" }
        assertTrue(urls.contains("https://cdn.test/media/video/${'$'}RepresentationID${'$'}/seg-${'$'}Number%05d${'$'}.m4s"))
        assertTrue(urls.contains("https://cdn.test/media/video/init.mp4"))
        assertTrue(out.contains("Number%05d"))
    }
    @Test fun segmentListAndProgressiveBaseAreRewritten() {
        val urls = mutableListOf<String>()
        DashManifestRewriter.rewrite("""<MPD><Period><AdaptationSet><Representation><SegmentList><Initialization sourceURL="init.mp4"/><SegmentURL media="../chunk.m4s?sig=x"/></SegmentList></Representation></AdaptationSet></Period></MPD>""",
            "https://cdn.test/path/main.mpd") { urls.add(it); it }
        assertTrue(urls.contains("https://cdn.test/path/init.mp4"))
        assertTrue(urls.contains("https://cdn.test/chunk.m4s?sig=x"))
    }
    @Test(expected = IllegalArgumentException::class) fun externalEntitiesAreRejected() {
        DashManifestRewriter.rewrite("""<!DOCTYPE MPD [<!ENTITY x SYSTEM="file:///etc/passwd">]><MPD>&x;</MPD>""", "https://cdn/a.mpd") { it }
    }
}
