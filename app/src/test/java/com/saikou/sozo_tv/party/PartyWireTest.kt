package com.saikou.sozo_tv.party

import com.saikou.sozo_tv.data.remote.party.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PartyWireTest {
    @Test fun `device direct payload never leaves the TV as room identity`() {
        assertEquals("https://catalog/movie", PartyIdentity.reference("direct::signed-stream-data", "https://catalog/movie"))
        assertEquals("provider:episode:1", PartyIdentity.reference("provider:episode:1", null))
        try { PartyIdentity.reference("direct::signed-stream-data", null); fail("must reject") } catch (_: IllegalStateException) { }
        try { PartyIdentity.reference("x".repeat(501), null); fail("must not silently truncate") } catch (_: IllegalArgumentException) { }
    }

    @Test fun `namespace frames preserve JSON payload and never interpret other events`() {
        val frame = PartyWire.event("party:chat", JSONObject().put("text", "quote \" and newline\n"))
        assertEquals("quote \" and newline\n", PartyWire.decode(frame)!!.second.getString("text"))
        assertNull(PartyWire.decode("42/premiere,[\"party:state\",{}]"))
        assertNull(PartyWire.decode("42/watch,broken"))
    }
    @Test fun `room codes normalize and reject path injection`() {
        assertEquals("AB12", PartyWire.normalizeCode(" ab12 "))
        try { PartyWire.normalizeCode("../room"); fail("must reject") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `playback uses elapsed local time and never rewinds when clocks differ`() {
        val snapshot = PartyPlayback.parse(JSONObject("""{"positionSec":40,"isPlaying":true,"rate":1.5,"serverTime":9999999999}"""), 1000)
        assertEquals(43000, snapshot.expected(3000))
        assertEquals(40000, snapshot.expected(500))
        assertEquals(40000, snapshot.copy(playing = false).expected(3000))
    }
}
