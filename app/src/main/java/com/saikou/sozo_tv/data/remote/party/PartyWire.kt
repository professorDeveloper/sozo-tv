package com.saikou.sozo_tv.data.remote.party

import org.json.JSONArray
import org.json.JSONObject

/** Text-only subset used by the backend: Engine.IO4 websocket + Socket.IO /watch. */
internal object PartyWire {
    fun authenticate(token: String) = "40/watch," + JSONObject().put("token", token)
    fun event(name: String, payload: JSONObject) = "42/watch," + JSONArray().put(name).put(payload)
    fun decode(message: String): Pair<String, JSONObject>? {
        if (!message.startsWith("42/watch,")) return null
        val data = runCatching { JSONArray(message.substringAfter("42/watch,")) }.getOrNull() ?: return null
        val event = data.optString(0)
        val payload = data.optJSONObject(1) ?: return null
        return event.takeIf { it.startsWith("party:") }?.let { it to payload }
    }
    fun normalizeCode(code: String): String = code.trim().uppercase(java.util.Locale.ROOT).also {
        require(it.matches(Regex("[A-Z0-9]{4,12}"))) { "Enter the room code (4–12 letters or numbers)" }
    }
}

/** Position uses a monotonic local receipt clock, never the server wall clock. */
data class PartyPlayback(val positionMs: Long, val playing: Boolean, val rate: Float, val receivedMs: Long) {
    fun expected(nowMs: Long): Long = (positionMs + if (playing) ((nowMs - receivedMs).coerceAtLeast(0) * rate).toLong() else 0).coerceAtLeast(0)
    companion object {
        fun parse(value: JSONObject, nowMs: Long) = PartyPlayback(
            (value.optDouble("positionSec", 0.0).takeIf { it.isFinite() }?.times(1000)?.toLong() ?: 0).coerceAtLeast(0),
            value.optBoolean("isPlaying"), value.optDouble("rate", 1.0).toFloat().takeIf { it.isFinite() }?.coerceIn(.25f, 3f) ?: 1f, nowMs)
    }
}

/** Local direct payloads contain signed URLs; share their catalog identity instead. */
internal object PartyIdentity {
    fun reference(mediaRef: String, contentUrl: String?): String {
        require(mediaRef.isNotBlank()) { "This video has no shareable episode reference" }
        val result = if (mediaRef.startsWith("direct::")) contentUrl?.takeIf { it.isNotBlank() && !it.startsWith("direct::") }
            ?: error("This video has no shareable catalog identity") else mediaRef
        require(result.length <= 500) { "This provider's episode reference is too long for watch parties" }
        return result
    }
}
