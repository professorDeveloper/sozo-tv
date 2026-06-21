package com.saikou.sozo_tv.data.extensions

import android.content.Context
import com.saikou.sozo_tv.app.MyApp
import org.json.JSONObject

/**
 * Bridges the extension world (keyed by `provider` + `contentUrl` strings) to the
 * legacy UI, which is keyed entirely on **Int** media ids (`HomeModel.id`,
 * `DetailModel.id`, player `getIntExtra("model")`, watch-history rows, …).
 *
 * Each unique `(provider, url)` pair is assigned a stable positive Int. The map is
 * persisted so ids survive process death (resume / continue-watching still work).
 */
object ExtensionContentRegistry {

    private const val PREFS = "ext_content_registry"
    private const val KEY_MAP = "map"      // { "<id>": {p,u,a,t,n} }
    private const val KEY_SEQ = "seq"

    data class Entry(
        val id: Int,
        val provider: String,
        val url: String,
        val isAnime: Boolean,
        val thumbnail: String?,
        val title: String?,
    )

    private val prefs by lazy { MyApp.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    // id -> Entry, and "provider|url" -> id
    private val byId = LinkedHashMap<Int, Entry>()
    private val byKey = HashMap<String, Int>()
    private var seq = 1000
    @Volatile private var loaded = false

    private fun key(provider: String, url: String) = "$provider|$url"

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        seq = prefs.getInt(KEY_SEQ, 1000)
        val raw = prefs.getString(KEY_MAP, "{}") ?: "{}"
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
        obj.keys().forEach { k ->
            val id = k.toIntOrNull() ?: return@forEach
            val o = obj.optJSONObject(k) ?: return@forEach
            val e = Entry(
                id = id,
                provider = o.optString("p"),
                url = o.optString("u"),
                isAnime = o.optBoolean("a", false),
                thumbnail = o.optString("t").ifEmpty { null },
                title = o.optString("n").ifEmpty { null },
            )
            byId[id] = e
            byKey[key(e.provider, e.url)] = id
        }
    }

    @Synchronized
    private fun persist() {
        val obj = JSONObject()
        byId.values.forEach { e ->
            obj.put(
                e.id.toString(),
                JSONObject().apply {
                    put("p", e.provider); put("u", e.url); put("a", e.isAnime)
                    put("t", e.thumbnail ?: ""); put("n", e.title ?: "")
                },
            )
        }
        prefs.edit().putString(KEY_MAP, obj.toString()).putInt(KEY_SEQ, seq).apply()
    }

    /** Assign (or reuse) the stable Int id for a card / detail target. */
    @Synchronized
    fun encode(provider: String, url: String, isAnime: Boolean, thumbnail: String? = null, title: String? = null): Int {
        ensureLoaded()
        val k = key(provider, url)
        byKey[k]?.let { existing ->
            // refresh thumbnail/title if we now have richer data
            val cur = byId[existing]
            if (cur != null && (cur.thumbnail.isNullOrEmpty() && !thumbnail.isNullOrEmpty())) {
                byId[existing] = cur.copy(thumbnail = thumbnail, title = title ?: cur.title)
                persist()
            }
            return existing
        }
        val id = ++seq
        val e = Entry(id, provider, url, isAnime, thumbnail, title)
        byId[id] = e
        byKey[k] = id
        persist()
        return id
    }

    fun encode(card: ExtCard): Int =
        encode(card.provider, card.contentUrl, card.isAnime, card.thumbnail, card.title)

    fun resolve(id: Int): Entry? {
        ensureLoaded()
        return byId[id]
    }
}
