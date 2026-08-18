package com.saikou.sozo_tv.data.remote.remote

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/** One instruction from the phone. Unknown types are dropped by the caller, not here. */
data class RemoteCommand(
    val type: String,
    val positionMs: Long? = null,
    val deltaMs: Long? = null,
    /** Volume only. Typed text arrives as [text] — one key, one type. */
    val value: Double? = null,
    val text: String? = null,
    val direction: String? = null,
    val contentUrl: String? = null,
    val provider: String? = null,
    val title: String? = null,
    val episodeIndex: Int? = null,
)

/** What this TV is doing, sent up so the phone's remote can show it. */
data class RemoteState(
    val deviceId: String,
    val screen: String? = null,
    val title: String? = null,
    val episode: String? = null,
    val playing: Boolean = false,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
)

/**
 * The TV's end of the remote-control channel.
 *
 * Holds one long-lived SSE connection and hands each command to [onCommand]. The
 * caller owns reconnection — this class deliberately returns when the stream
 * ends rather than looping, so the retry policy (and its backoff) lives in one
 * place next to the lifecycle that should stop it.
 *
 * The stream is parsed by hand instead of pulling in okhttp-sse: the server
 * sends `event:` / `data:` pairs and comment heartbeats, which is the whole of
 * the protocol we use, and a dependency for that is not worth its APK size.
 */
class RemoteControlClient(
    okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {

    // The stream never goes quiet for long — the server heartbeats every 25s —
    // but it must never be read-timed-out for being idle between commands.
    private val streamClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val postClient: OkHttpClient = okHttpClient

    /**
     * Opens the channel and blocks until it closes.
     *
     * Returns normally on a clean close and throws on a transport failure, so
     * the caller can tell "the server let go" from "the network is gone" —
     * they deserve different backoff.
     */
    suspend fun stream(
        deviceId: String,
        onOpen: () -> Unit,
        onCommand: (RemoteCommand) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val token = tokenProvider() ?: throw IllegalStateException("No access token")

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}$PATH_STREAM?deviceId=$deviceId")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .build()

        streamClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RemoteStreamException(response.code)
            }
            onOpen()

            val reader = response.body.charStream().buffered()
            var event: String? = null
            val data = StringBuilder()

            reader.forEachLineCancellable { line ->
                when {
                    // A comment. The heartbeat arrives as one; it exists to keep
                    // proxies from dropping us, and carries nothing to parse.
                    line.startsWith(":") -> Unit

                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()

                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.removePrefix("data:").trim())
                    }

                    // Blank line ends an event.
                    line.isEmpty() -> {
                        if (event == "command" && data.isNotEmpty()) {
                            parse(data.toString())?.let(onCommand)
                        }
                        event = null
                        data.setLength(0)
                    }
                }
            }
        }
    }

    private fun parse(json: String): RemoteCommand? = try {
        gson.fromJson(json, RemoteCommand::class.java)?.takeIf { it.type.isNotBlank() }
    } catch (_: JsonSyntaxException) {
        // A malformed frame must not tear down a working channel.
        null
    }

    /** Best effort: the phone showing a slightly stale position beats a crash. */
    suspend fun report(state: RemoteState): Boolean = withContext(Dispatchers.IO) {
        val token = tokenProvider() ?: return@withContext false
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}$PATH_STATE")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(state).toRequestBody(JSON))
            .build()

        runCatching {
            postClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private inline fun BufferedReader.forEachLineCancellable(action: (String) -> Unit) {
        while (true) {
            val line = readLine() ?: return
            action(line)
        }
    }

    companion object {
        const val PATH_STREAM = "/remote/stream"
        const val PATH_STATE = "/remote/state"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/** Non-2xx on the stream itself. 401 means the token died; 404 means this session is gone. */
class RemoteStreamException(val code: Int) : Exception("Remote stream failed: $code")
