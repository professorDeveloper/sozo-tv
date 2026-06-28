package com.saikou.sozo_tv.engine.server

import android.content.Context
import com.saikou.sozo_tv.engine.webjs.WebJsRuntime
import org.json.JSONArray
import org.json.JSONObject

class ServerHost(
    context: Context,
    private val client: ApisozoClient = ApisozoClient(),
) {
    private val webjs by lazy { WebJsRuntime(context, client) }

    private data class Meta(
        val mode: String,
        val scope: String?,
        val extractorName: String?,
        val extractorVersion: Int,
    )

    private val meta = LinkedHashMap<String, Meta>()

    fun ensureLoaded() {
        if (meta.isEmpty()) runCatching { providersJson() }
    }

    fun providersJson(): String {
        val body = client.get("/contents/providers")
            ?: throw IllegalStateException("Server unreachable — check your connection")
        val arr = parseArray(body)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = firstNonEmpty(o, "id", "_id", "slug") ?: continue
            val ext = o.optJSONObject("extractor")
            val mode = o.optString("mode").ifEmpty { "server" }
            val scope = ext?.let { it.optString("scope").ifEmpty { "resolveMedia" } }
            meta[id] = Meta(mode, scope, ext?.optString("name"), ext?.optInt("version", 0) ?: 0)
            out.put(JSONObject().apply {
                put("id", "sv:$id")
                put("name", o.optString("name").ifEmpty { id })
                put("icon", o.optString("image"))
                put("baseUrl", o.optString("url"))
                put("group", "server")
                put("nsfw", false)
                put("mode", mode)
                if (ext != null) {
                    put("extractor", JSONObject().apply {
                        put("name", ext.optString("name"))
                        put("scope", scope)
                    })
                }
            })
        }
        return out.toString()
    }

    suspend fun getMainPageJson(provider: String, page: Int): String {
        val b = bare(provider)
        ensureMeta()
        val m = meta[b]
        val soplay = if (usesJsCatalog(m)) {
            webjs.callProvider(m!!.extractorName!!, m.extractorVersion, "getHome", JSONArray())
        } else {
            client.get("/contents/home", mapOf("provider" to b))
        }
        return translateHome(soplay, b)
    }

    suspend fun getSectionJson(provider: String, data: String, page: Int): String {
        val b = bare(provider)
        ensureMeta()
        val m = meta[b]
        val sep = data.indexOf("::")
        val type = if (sep >= 0) data.substring(0, sep) else data
        val slug = if (sep >= 0) data.substring(sep + 2) else ""
        val soplay = if (usesJsCatalog(m)) {
            val token = slug.ifEmpty { type }
            webjs.callProvider(m!!.extractorName!!, m.extractorVersion, "getCategory", JSONArray().put(token).put(page))
        } else {
            val path = if (slug.isEmpty()) "/contents/$type" else "/contents/$type/$slug"
            client.get(path, mapOf("provider" to b, "page" to page.toString()))
        }
        return translatePage(soplay, b)
    }

    suspend fun searchJson(provider: String, query: String): String {
        val b = bare(provider)
        ensureMeta()
        val m = meta[b]
        val soplay = if (usesJsCatalog(m)) {
            webjs.callProvider(m!!.extractorName!!, m.extractorVersion, "search", JSONArray().put(query).put(1))
        } else {
            client.get("/contents/search", mapOf("provider" to b, "q" to query, "page" to "1"))
        }
        return translatePage(soplay, b)
    }

    suspend fun getGenresJson(provider: String): String {
        val b = bare(provider)
        ensureMeta()
        if (usesJsCatalog(meta[b])) return "[]"
        val body = client.get("/contents/genres", mapOf("provider" to b)) ?: return "[]"
        val items = parseObject(body).optJSONArray("items") ?: JSONArray()
        val out = JSONArray()
        for (i in 0 until items.length()) {
            val g = items.optJSONObject(i) ?: continue
            out.put(JSONObject().apply {
                put("provider", prefixed(b))
                put("name", g.optString("name"))
                put("slug", g.optString("slug"))
                put("image", g.optString("image"))
            })
        }
        return out.toString()
    }

    suspend fun loadJson(provider: String, url: String): String {
        val b = bare(provider)
        ensureMeta()
        val m = meta[b]
        val detail: String?
        val episodes: String?
        if (usesJsCatalog(m)) {
            detail = webjs.callProvider(m!!.extractorName!!, m.extractorVersion, "getDetail", JSONArray().put(url))
            episodes = webjs.callProvider(m.extractorName!!, m.extractorVersion, "getEpisodes", JSONArray().put(url))
        } else {
            detail = client.get("/contents/detail", mapOf("provider" to b, "url" to url))
            episodes = client.get(
                "/contents/episodes",
                mapOf("provider" to b, "url" to url, "page" to "1", "size" to "100", "sort" to "asc"),
            )
        }
        return translateDetail(detail, episodes, b, url)
    }

    suspend fun loadLinksJson(provider: String, data: String): String {
        val b = bare(provider)
        ensureMeta()
        val m = meta[b]
        val soplay = if (usesJsResolve(m)) {
            webjs.callProvider(
                m!!.extractorName!!,
                m.extractorVersion,
                "resolveMedia",
                JSONArray().put(data).put(JSONObject().put("lang", "sub")),
            )
        } else {
            client.get("/contents/media", mapOf("provider" to b, "ref" to data))
        }
        return translateMedia(soplay)
    }

    private fun usesJsCatalog(m: Meta?) = m?.scope == "all" && !m.extractorName.isNullOrEmpty()

    private fun usesJsResolve(m: Meta?) =
        (m?.scope == "all" || m?.scope == "resolveMedia") && !m.extractorName.isNullOrEmpty()

    private fun ensureMeta() {
        if (meta.isEmpty()) runCatching { providersJson() }
    }

    private fun translateHome(soplay: String?, bareProvider: String): String {
        val o = parseObject(soplay ?: return "{}")
        val sectionsIn = o.optJSONArray("sections") ?: JSONArray()
        val sectionsOut = JSONArray()
        for (i in 0 until sectionsIn.length()) {
            val s = sectionsIn.optJSONObject(i) ?: continue
            val items = cards(s.optJSONArray("items"), bareProvider)
            if (items.length() == 0) continue
            val va = s.optJSONObject("viewAll")
            val type = va?.optString("type").orEmpty()
            val slug = va?.optString("slug").orEmpty()
            sectionsOut.put(JSONObject().apply {
                put("key", s.optString("key"))
                put("label", s.optString("label"))
                put("viewAll", JSONObject().put("slug", "$type::$slug"))
                put("items", items)
            })
        }
        return JSONObject().apply {
            put("provider", prefixed(bareProvider))
            put("banner", cards(o.optJSONArray("banner"), bareProvider))
            put("sections", sectionsOut)
        }.toString()
    }

    private fun translatePage(soplay: String?, bareProvider: String): String {
        val o = parseObject(soplay ?: return "{}")
        return JSONObject().apply {
            put("provider", prefixed(bareProvider))
            put("items", cards(o.optJSONArray("items"), bareProvider))
            put("page", o.optInt("page", 1))
            put("totalPages", o.optInt("totalPages", 1))
        }.toString()
    }

    private fun translateDetail(detail: String?, episodes: String?, bareProvider: String, url: String): String {
        val d = parseObject(detail ?: return "{}")
        val ep = parseObject(episodes ?: "{}")
        val epsIn = ep.optJSONArray("episodes") ?: d.optJSONArray("episodes") ?: JSONArray()
        val epsOut = JSONArray()
        for (i in 0 until epsIn.length()) {
            val e = epsIn.optJSONObject(i) ?: continue
            epsOut.put(JSONObject().apply {
                put("episode", e.optInt("episode", i + 1))
                put("label", e.optString("label"))
                put("mediaRef", e.optString("mediaRef"))
                put("image", e.optString("image"))
                put("overview", e.optString("overview"))
                put("airdate", e.optString("airdate"))
                put("runtime", e.optString("runtime"))
            })
        }
        val isSerial = d.optBoolean("isSerial", false) || ep.optBoolean("isSerial", false) || epsOut.length() > 1
        if (epsOut.length() == 0) {
            epsOut.put(JSONObject().apply {
                put("episode", 1)
                put("label", "Movie")
                put("mediaRef", url)
            })
        }
        val castIn = d.optJSONArray("cast") ?: JSONArray()
        val castOut = JSONArray()
        for (i in 0 until castIn.length()) {
            val c = castIn.optJSONObject(i) ?: continue
            castOut.put(JSONObject().apply {
                put("name", c.optString("name"))
                put("image", c.optString("image"))
            })
        }
        return JSONObject().apply {
            put("provider", prefixed(bareProvider))
            put("contentUrl", d.optString("contentUrl").ifEmpty { url })
            put("title", d.optString("title"))
            put("description", d.optString("description"))
            put("thumbnail", d.optString("thumbnail"))
            if (d.has("year") && !d.isNull("year")) put("year", d.opt("year"))
            put("duration", d.optString("duration"))
            put("director", d.optString("director"))
            put("genres", d.optJSONArray("genres") ?: JSONArray())
            put("isSerial", isSerial)
            put("cast", castOut)
            put("related", cards(d.optJSONArray("related"), bareProvider))
            put("episodes", epsOut)
        }.toString()
    }

    private fun translateMedia(soplay: String?): String {
        val o = parseObject(soplay ?: return "{}")
        val srcIn = o.optJSONArray("videoSources") ?: JSONArray()
        val srcOut = JSONArray()
        for (i in 0 until srcIn.length()) {
            val s = srcIn.optJSONObject(i) ?: continue
            val link = s.optString("videoUrl")
            if (link.isEmpty()) continue
            srcOut.put(JSONObject().apply {
                put("quality", s.optString("quality"))
                put("videoUrl", link)
                put("type", s.optString("type"))
                put("isDefault", s.optBoolean("isDefault", i == 0))
                put("headers", s.optJSONObject("headers") ?: JSONObject())
            })
        }
        val subIn = o.optJSONArray("subtitles") ?: JSONArray()
        val subOut = JSONArray()
        for (i in 0 until subIn.length()) {
            val s = subIn.optJSONObject(i) ?: continue
            val file = firstNonEmpty(s, "file", "url", "src") ?: continue
            subOut.put(JSONObject().apply {
                put("label", firstNonEmpty(s, "label", "lang", "language") ?: "Subtitle")
                put("file", file)
                put("default", s.optBoolean("default", s.optBoolean("isDefault", false)))
            })
        }
        return JSONObject().apply {
            put("videoUrl", o.optString("videoUrl"))
            put("type", o.optString("type"))
            put("headers", o.optJSONObject("headers") ?: JSONObject())
            put("videoSources", srcOut)
            put("subtitles", subOut)
        }.toString()
    }

    private fun cards(arr: JSONArray?, bareProvider: String): JSONArray {
        val out = JSONArray()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val p = o.optString("provider").ifEmpty { bareProvider }
            val contentUrl = o.optString("contentUrl").ifEmpty { o.optString("slug") }
            if (contentUrl.isEmpty()) continue
            out.put(JSONObject().apply {
                put("provider", prefixed(p))
                put("title", o.optString("title"))
                put("contentUrl", contentUrl)
                put("thumbnail", o.optString("thumbnail"))
                put("type", o.optString("category").ifEmpty { o.optString("type") })
            })
        }
        return out
    }

    private fun bare(provider: String) = provider.removePrefix("sv:")

    private fun prefixed(id: String) = if (id.startsWith("sv:")) id else "sv:$id"

    private fun parseArray(body: String): JSONArray {
        if (body.trimStart().startsWith("[")) {
            return runCatching { JSONArray(body) }.getOrDefault(JSONArray())
        }
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return JSONArray()
        return o.optJSONArray("items") ?: o.optJSONArray("data") ?: JSONArray()
    }

    private fun parseObject(body: String): JSONObject =
        runCatching { JSONObject(body) }.getOrDefault(JSONObject())

    private fun firstNonEmpty(o: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            val v = o.optString(k)
            if (v.isNotEmpty()) return v
        }
        return null
    }
}
