package com.saikou.sozo_tv.torrent

import com.saikou.sozo_tv.engine.torrent.TorrentFiles
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class TorrentFilesTest {
    @Test fun `episode matching avoids samples and episode number prefixes`() {
        val files = TorrentFiles.parse(JSONArray("""[{"id":1,"path":"Show.S01E02.mkv","length":1000},{"id":2,"path":"Show.S01E020.mkv","length":2000},{"id":3,"path":"sample.S01E02.mkv","length":5000}]"""))
        assertEquals(1, TorrentFiles.select(files, 2).id)
    }
    @Test fun `invalid file indices and nonvideo files cannot become streams`() {
        assertTrue(TorrentFiles.parse(JSONArray("""[{"id":0,"path":"film.mp4"},{"id":1,"path":"readme.txt"}]""")).isEmpty())
    }
    @Test fun `ambiguous season pack never silently starts a different episode`() {
        val files = listOf(TorrentFiles.Entry(1,"Show.E01.mkv",100), TorrentFiles.Entry(2,"Show.E03.mkv",200))
        try { TorrentFiles.select(files, 2); fail("expected matching error") } catch (_: IllegalStateException) { }
    }
}
