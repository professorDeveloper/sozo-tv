package com.saikou.sozo_tv.domain.player

/** Only explicit server-provider envelopes are portable; plugin identities stay opaque. */
object HistoryIdentity {
    data class Content(val provider: String, val content: String, val migrated: Boolean)
    fun content(provider: String, content: String): Content {
        val p = provider.trim()
        val c = content.trim()
        val prefix = "$p\u0001"
        if (p.startsWith("sv:") && p.length > 3 && c.startsWith(prefix)) {
            val raw = c.removePrefix(prefix)
            if (raw.isNotBlank() && '\u0001' !in raw) return Content(p.removePrefix("sv:"), raw, true)
        }
        return Content(p, c, false)
    }

    fun canonicalKey(key: String): String {
        val split = key.indexOf('|')
        if (split < 0) return key
        val value = content(key.substring(0, split), key.substring(split + 1))
        return if (value.migrated) "${value.provider}|${value.content}" else key
    }

    fun key(provider: String, content: String, isSeries: Boolean, episode: Int?): String? {
        val value = content(provider, content)
        if (value.content.isBlank()) return null
        val base = "${value.provider}|${value.content}"
        return if (isSeries && episode != null) "$base|e$episode" else base
    }

    fun legacyKey(provider: String, content: String, isSeries: Boolean, episode: Int?): String? {
        if (content.isBlank()) return null
        val base = "${provider.trim()}|${content.trim()}"
        return if (isSeries && episode != null) "$base|e$episode" else base
    }
}
