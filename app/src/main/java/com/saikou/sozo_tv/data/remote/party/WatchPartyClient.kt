package com.saikou.sozo_tv.data.remote.party

import com.saikou.sozo_tv.BuildConfig
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Authenticated room transport. Its client retains platform TLS and never carries source cookies. */
class WatchPartyClient(private val tokenProvider: suspend (Boolean) -> String?, http: OkHttpClient,
    private val scope: CoroutineScope, private val onEvent: (String, JSONObject) -> Unit,
    private val onStatus: (String) -> Unit, baseUrl: String = BuildConfig.SOZO_API_BASE_URL) {
    private val rest = http
    private val socketClient = http.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).callTimeout(0, TimeUnit.MILLISECONDS).build()
    private var socket: WebSocket? = null
    private var connectJob: Job? = null
    private var retry = 0
    private var generation = 0
    private var closed = true
    private var connected = false
    var code: String? = null
        private set
    private val base = baseUrl.trimEnd('/')

    suspend fun create(content: JSONObject): String {
        val response = request("POST", "/watch-party", JSONObject().put("content", content))
        return response.getString("code").also(::join)
    }
    fun join(raw: String) {
        val normalized = PartyWire.normalizeCode(raw)
        leave()
        code = normalized; closed = false; retry = 0
        reconnect(false)
    }
    private fun reconnect(refresh: Boolean) {
        if (closed) return
        connectJob?.cancel()
        val current = ++generation
        connected = false
        socket?.cancel()
        connectJob = scope.launch {
            if (retry > 0) delay((1000L shl retry.coerceAtMost(4)).coerceAtMost(15_000))
            try {
                val token = tokenProvider(refresh) ?: error("Sign in to join a watch party")
                if (current != generation || closed) return@launch
                onStatus(if (retry > 0) "Reconnecting…" else "Connecting…")
                val origin = base.toHttpUrl().newBuilder().encodedPath("/socket.io/")
                    .query("EIO=4&transport=websocket").build()
                socket = socketClient.newWebSocket(Request.Builder().url(origin).build(), object : WebSocketListener() {
                    override fun onMessage(ws: WebSocket, text: String) {
                        scope.launch {
                            if (current != generation || closed) return@launch
                            when {
                                text.startsWith("0{") -> ws.send(PartyWire.authenticate(token))
                                text == "2" -> ws.send("3")
                                text.startsWith("40/watch,") -> {
                                    connected = true; retry = 0
                                    emit("party:join", JSONObject()); onStatus("Joining room…")
                                }
                                text.startsWith("44/watch,") -> {
                                    if (!refresh) reconnect(true) else { onStatus("Sign in again to join this room"); leave() }
                                }
                                else -> PartyWire.decode(text)?.let { (event, payload) -> onEvent(event, payload) }
                            }
                        }
                    }
                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) = lost(current)
                    override fun onClosed(ws: WebSocket, code: Int, reason: String) = lost(current)
                    override fun onClosing(ws: WebSocket, code: Int, reason: String) { ws.close(code, reason) }
                })
                delay(15_000)
                if (current == generation && !connected && !closed) { onStatus("Room connection timed out"); lost(current) }
            } catch (c: CancellationException) { throw c }
            catch (e: Exception) { onStatus(e.message ?: "Could not connect"); lost(current) }
        }
    }
    private fun lost(current: Int) {
        scope.launch {
            if (current != generation || closed) return@launch
            connected = false; retry++
            if (retry > 6) { onStatus("Connection lost. Leave and join the room again."); return@launch }
            reconnect(false)
        }
    }
    fun emit(event: String, payload: JSONObject) {
        val room = code ?: return
        if (connected) socket?.send(PartyWire.event(event, JSONObject(payload.toString()).put("code", room)))
    }
    suspend fun closeRoom() { code?.let { request("DELETE", "/watch-party/$it", null) }; leave() }
    fun leave() {
        emit("party:leave", JSONObject())
        closed = true; connected = false; generation++; connectJob?.cancel()
        socket?.close(1000, "Leaving room"); socket = null; code = null
    }
    private suspend fun request(method: String, path: String, payload: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val token = tokenProvider(false) ?: error("Sign in to use watch parties")
        val body = payload?.toString()?.toRequestBody("application/json".toMediaType())
        rest.newCall(Request.Builder().url(base + path).header("Authorization", "Bearer $token")
            .method(method, body).build()).execute().use { response ->
            val data = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrDefault(JSONObject())
            check(response.isSuccessful) { data.optString("message").ifBlank { "Room request failed (HTTP ${response.code})" } }
            data
        }
    }
}
