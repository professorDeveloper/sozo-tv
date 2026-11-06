package com.saikou.sozo_tv.party

import com.saikou.sozo_tv.data.remote.party.WatchPartyClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run with SOZO_PARTY_FIXTURE_URL and tools/watch-party-fixture.cjs (real backend gateway). */
class WatchPartyTransportTest {
    @Test fun `two clients authenticate rejoin and enforce host controls with the backend gateway`() = runBlocking {
        val base = System.getenv("SOZO_PARTY_FIXTURE_URL")
        assumeTrue("Local gateway fixture is opt-in", !base.isNullOrBlank())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val hostEvents = Channel<Pair<String, JSONObject>>(Channel.UNLIMITED)
        val guestEvents = Channel<Pair<String, JSONObject>>(Channel.UNLIMITED)
        val http = OkHttpClient()
        val host = WatchPartyClient({ "fixture-host" }, http, scope, { e, p -> hostEvents.trySend(e to p) }, {}, base!!)
        val guest = WatchPartyClient({ "fixture-guest" }, http, scope, { e, p -> guestEvents.trySend(e to p) }, {}, base)
        suspend fun event(events: Channel<Pair<String, JSONObject>>, name: String): JSONObject = withTimeout(15_000) {
            while (true) { val next = events.receive(); if (next.first == name) return@withTimeout next.second }
            @Suppress("UNREACHABLE_CODE") error("No event")
        }
        try {
            val code = host.create(JSONObject().put("provider", "fixture").put("mediaRef", "episode-1").put("streamUrl", "must-not-leak"))
            val state = event(hostEvents, "party:state")
            assertFalse(state.getJSONObject("content").has("streamUrl"))
            guest.join(code); event(guestEvents, "party:state")
            host.emit("party:control", JSONObject().put("action", "play").put("positionSec", 12.5))
            assertTrue(event(guestEvents, "party:sync").getJSONObject("playback").getBoolean("isPlaying"))
            guest.emit("party:control", JSONObject().put("action", "seek").put("positionSec", 100))
            assertEquals("forbidden", event(guestEvents, "party:error").getString("code"))
            guest.emit("party:chat", JSONObject().put("text", "TV joined"))
            assertEquals("TV joined", event(hostEvents, "party:chat").getString("text"))
            delay(2500) // crosses two Engine.IO ping cycles (one-second fixture heartbeat).
            http.newCall(Request.Builder().url(base.removeSuffix("/api") + "/fixture/disconnect")
                .header("Authorization", "Bearer fixture-host").post("".toRequestBody()).build()).execute().close()
            val rejoined = event(hostEvents, "party:state"); event(guestEvents, "party:state")
            val controller = if (rejoined.optString("hostUserId") == "host") host else guest
            controller.emit("party:control", JSONObject().put("action", "pause").put("positionSec", 20))
            assertFalse(event(guestEvents, "party:sync").getJSONObject("playback").getBoolean("isPlaying"))
            host.closeRoom(); event(guestEvents, "party:closed")
        } finally { host.leave(); guest.leave(); scope.cancel(); http.dispatcher.executorService.shutdown(); http.connectionPool.evictAll() }
        Unit
    }
}
