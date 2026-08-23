package com.saikou.sozo_tv.data.extensions

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Plain Kotlin models mirroring the JSON shapes produced by the ported extension
 * hosts (`engine.aniyomi.AniyomiHost` / `engine.cloudstream.PluginHost`). Both
 * engines emit identical shapes, so a single set of parsers serves both.
 *
 * Provider ids are prefixed: `an:<id>` (Aniyomi) and `cs:<name>` (CloudStream).
 */

data class ExtProvider(
    val id: String,
    val name: String,
    val lang: String? = null,
    val baseUrl: String? = null,
    val icon: String? = null,
    val nsfw: Boolean = false,
    val repo: String? = null,
    val group: String,
    val mode: String = "client",
    val extractorName: String? = null,
    val extractorScope: String? = null,
) {
    val isAniyomi get() = id.startsWith("an:")
    val isServer get() = mode == "server"
    val isHybrid get() = mode == "hybrid"
    val scopesAll get() = extractorScope == "all"
    val scopesResolveMedia get() = extractorScope == "all" || extractorScope == "resolveMedia"
}

data class ExtRepo(val url: String, val name: String)

/** A content card (home row item / search result / related item). */
data class ExtCard(
    val provider: String,
    val title: String,
    val contentUrl: String,
    val thumbnail: String?,
    val type: String?,
) {
    val isAnime get() = provider.startsWith("an:") || (type?.equals("Anime", true) == true)
}

data class ExtSection(
    val key: String,
    val label: String,
    val slug: String?,   // viewAll data passed to getSection
    val items: List<ExtCard>,
)

data class ExtHome(
    val provider: String,
    val banner: List<ExtCard>,
    val sections: List<ExtSection>,
)

data class ExtPage(
    val provider: String,
    val items: List<ExtCard>,
    val page: Int,
    val totalPages: Int,
)

data class ExtGenre(
    val provider: String,
    val name: String,
    val slug: String?,
    val image: String?,
)

data class ExtCast(val name: String, val image: String?, val character: String?)

data class ExtEpisode(
    val episode: Int,
    val label: String,
    val mediaRef: String,
    val image: String?,
    val season: Int?,
    val overview: String?,
    val airdate: String?,
    val runtime: String?,
)

data class ExtDetail(
    val provider: String,
    val contentUrl: String,
    val title: String,
    val description: String,
    val thumbnail: String?,
    val banner: String?,
    val year: Int?,
    val duration: String?,
    val director: String?,
    val genres: List<String>,
    val type: String?,
    val isSerial: Boolean,
    val cast: List<ExtCast>,
    val related: List<ExtCard>,
    val episodes: List<ExtEpisode>,
    val anilistId: Int? = null,
    val malId: Int? = null,
)

data class ExtVideoSource(
    val quality: String,
    val videoUrl: String,
    val type: String,           // "hls" | "http"
    val host: String?,
    val isDefault: Boolean,
    val headers: Map<String, String>,
    val useLocalProxy: Boolean = false,
    val localProxy: String? = null,        // raw JSON
    val requestTransform: String? = null,  // raw JSON
    /**
     * Server-set capability flag: this source's real manifest only exists after
     * the page's own JS runs, so play it by sniffing a headless WebView rather
     * than handing the url to ExoPlayer. Never branch on the provider id — any
     * source carrying this flag takes the same path, so a new site like it is a
     * backend-only change.
     */
    val useWebViewSniff: Boolean = false,
    val sniff: String? = null,             // raw JSON: patterns/timeoutMs/headers/blockHosts
)

data class ExtSubtitle(val label: String, val file: String, val default: Boolean)

data class ExtMedia(
    val videoUrl: String?,
    val type: String?,
    val headers: Map<String, String>,
    val sources: List<ExtVideoSource>,
    val subtitles: List<ExtSubtitle>,
    val thumbnails: String? = null,
)

// ---------- parsing ----------

internal object ExtParser {

    private const val TAG = "ExtParser"

    private fun JSONObject.headersMap(key: String): Map<String, String> {
        val o = optJSONObject(key) ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        o.keys().forEach { k -> out[k] = o.optString(k) }
        return out
    }

    private fun JSONObject.strOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifEmpty { null }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key).takeIf { it != 0 || optString(key) == "0" } else null

    fun card(o: JSONObject): ExtCard = ExtCard(
        provider = o.optString("provider"),
        title = o.optString("title"),
        contentUrl = o.optString("contentUrl").ifEmpty { o.optString("slug") },
        thumbnail = o.strOrNull("thumbnail"),
        type = o.strOrNull("type"),
    )

    private fun cards(arr: JSONArray?): List<ExtCard> {
        if (arr == null) return emptyList()
        // Only contentUrl is load-bearing — it is what opens the title. A name is decoration:
        // several providers (NetMirror's Netflix/Prime/Hotstar rows among them) ship
        // poster-only cards with no name at all, and also demanding a title discarded an
        // entire, working home page as "this source has no home page right now".
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::card) }
            .filter { it.contentUrl.isNotEmpty() }
    }

    fun providers(json: String): List<ExtProvider> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifEmpty { return@mapNotNull null }
            val extractor = o.optJSONObject("extractor")
            ExtProvider(
                id = id,
                name = o.optString("name").ifEmpty { id },
                lang = o.strOrNull("lang"),
                baseUrl = o.strOrNull("baseUrl"),
                icon = o.strOrNull("icon"),
                nsfw = o.optBoolean("nsfw", false),
                repo = o.strOrNull("repo"),
                group = o.optString("group").ifEmpty { if (id.startsWith("an:")) "aniyomi" else "cloudstream" },
                mode = o.optString("mode").ifEmpty { "client" },
                extractorName = extractor?.strOrNull("name"),
                extractorScope = extractor?.strOrNull("scope"),
            )
        }
    }

    fun repos(json: String): List<ExtRepo> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            val o = arr.optJSONObject(it) ?: return@mapNotNull null
            ExtRepo(o.optString("url"), o.optString("name"))
        }
    }

    fun home(json: String): ExtHome {
        // A parse failure used to fall through to an empty JSONObject without a word, so a
        // provider whose host said "5 sections, 12 banner" reached the UI as "this source has
        // no home page". Say which of the two actually happened.
        val parsed = runCatching { JSONObject(json) }
        parsed.exceptionOrNull()?.let {
            Log.e(TAG, "home(): malformed JSON (${json.length} chars): ${it.message}")
        }
        val o = parsed.getOrNull() ?: JSONObject()
        val sections = o.optJSONArray("sections")
        val list = if (sections == null) emptyList() else (0 until sections.length()).mapNotNull { i ->
            val s = sections.optJSONObject(i) ?: return@mapNotNull null
            val items = cards(s.optJSONArray("items"))
            if (items.isEmpty()) return@mapNotNull null
            ExtSection(
                key = s.optString("key"),
                label = s.optString("label"),
                slug = s.optJSONObject("viewAll")?.strOrNull("slug"),
                items = items,
            )
        }
        val banner = cards(o.optJSONArray("banner"))
        val rawSections = sections?.length() ?: 0
        val rawBanner = o.optJSONArray("banner")?.length() ?: 0
        if ((rawSections > 0 || rawBanner > 0) && list.isEmpty() && banner.isEmpty()) {
            // Everything the host sent was discarded on the way in. Without this the drop is
            // invisible and looks like a dead source.
            Log.e(
                TAG,
                "home(): dropped every card - raw sections=$rawSections banner=$rawBanner; " +
                    "every card lacked a usable contentUrl/slug; first raw card = " +
                    (o.optJSONArray("banner")?.optJSONObject(0)
                        ?: sections?.optJSONObject(0)?.optJSONArray("items")?.optJSONObject(0))
            )
        }
        return ExtHome(o.optString("provider"), banner, list)
    }

    fun page(json: String): ExtPage {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: JSONObject()
        return ExtPage(
            provider = o.optString("provider"),
            items = cards(o.optJSONArray("items")),
            page = o.optInt("page", 1),
            totalPages = o.optInt("totalPages", 1),
        )
    }

    fun genres(json: String): List<ExtGenre> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            val o = arr.optJSONObject(it) ?: return@mapNotNull null
            ExtGenre(o.optString("provider"), o.optString("name"), o.strOrNull("slug"), o.strOrNull("image"))
        }
    }

    fun detail(json: String): ExtDetail? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (o.optString("contentUrl").isEmpty() && o.optString("title").isEmpty()) return null
        val genresArr = o.optJSONArray("genres")
        val genres = if (genresArr == null) emptyList() else
            (0 until genresArr.length()).map { genresArr.optString(it) }.filter { it.isNotEmpty() }
        val castArr = o.optJSONArray("cast")
        val cast = if (castArr == null) emptyList() else (0 until castArr.length()).mapNotNull { i ->
            val c = castArr.optJSONObject(i) ?: return@mapNotNull null
            ExtCast(c.optString("name"), c.strOrNull("image"), c.strOrNull("character"))
        }
        val epsArr = o.optJSONArray("episodes")
        val eps = if (epsArr == null) emptyList() else (0 until epsArr.length()).mapNotNull { i ->
            val e = epsArr.optJSONObject(i) ?: return@mapNotNull null
            ExtEpisode(
                episode = e.optInt("episode", i + 1),
                label = e.optString("label").ifEmpty { "Episode ${e.optInt("episode", i + 1)}" },
                mediaRef = e.optString("mediaRef"),
                image = e.strOrNull("image"),
                season = e.intOrNull("season"),
                overview = e.strOrNull("overview"),
                airdate = e.strOrNull("airdate"),
                runtime = e.strOrNull("runtime"),
            )
        }
        return ExtDetail(
            provider = o.optString("provider"),
            contentUrl = o.optString("contentUrl").ifEmpty { o.optString("contentId") },
            title = o.optString("title"),
            description = o.optString("description"),
            thumbnail = o.strOrNull("thumbnail"),
            banner = o.strOrNull("banner"),
            year = o.intOrNull("year"),
            duration = o.strOrNull("duration"),
            director = o.strOrNull("director"),
            genres = genres,
            type = o.strOrNull("type"),
            isSerial = o.optBoolean("isSerial", eps.size > 1),
            anilistId = o.optJSONObject("syncIds")?.intOrNull("anilist"),
            malId = o.optJSONObject("syncIds")?.intOrNull("mal")
                ?: o.optJSONObject("syncIds")?.intOrNull("myanimelist"),
            cast = cast,
            related = cards(o.optJSONArray("related")),
            episodes = eps,
        )
    }

    fun media(json: String): ExtMedia {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: JSONObject()
        val srcArr = o.optJSONArray("videoSources")
        val sources = if (srcArr == null) emptyList() else (0 until srcArr.length()).mapNotNull { i ->
            val s = srcArr.optJSONObject(i) ?: return@mapNotNull null
            val url = s.optString("videoUrl").ifEmpty { return@mapNotNull null }
            ExtVideoSource(
                quality = s.optString("quality").ifEmpty { "Source" },
                videoUrl = url,
                type = s.optString("type").ifEmpty { "hls" },
                host = s.strOrNull("host"),
                isDefault = s.optBoolean("isDefault", i == 0),
                headers = s.headersMap("headers"),
                useLocalProxy = s.optBoolean("useLocalProxy", false),
                // Fall back to the media-level flag: a provider may declare the
                // capability once for the whole payload rather than per source.
                useWebViewSniff = s.optBoolean("useWebViewSniff", o.optBoolean("useWebViewSniff", false)),
                sniff = (s.optJSONObject("sniff") ?: o.optJSONObject("sniff"))?.toString(),
                localProxy = s.optJSONObject("localProxy")?.toString(),
                requestTransform = s.optJSONObject("requestTransform")?.toString(),
            )
        }
        val subArr = o.optJSONArray("subtitles")
        val subs = if (subArr == null) emptyList() else (0 until subArr.length()).mapNotNull { i ->
            val s = subArr.optJSONObject(i) ?: return@mapNotNull null
            val file = s.optString("file").ifEmpty { return@mapNotNull null }
            ExtSubtitle(s.optString("label").ifEmpty { "Subtitle" }, file, s.optBoolean("default", false))
        }
        // Seek-preview thumbnails: soplay emits a bare VTT url String or a {url,type,template,...}
        // object. Forward the url so the player's VttSpriteThumbnailLoader can render it again.
        val thumb = o.opt("thumbnails")
        val thumbUrl = when (thumb) {
            is String -> thumb.ifEmpty { null }
            is JSONObject -> thumb.strOrNull("url") ?: thumb.strOrNull("template")
            else -> null
        }
        return ExtMedia(
            videoUrl = o.strOrNull("videoUrl"),
            type = o.strOrNull("type"),
            headers = o.headersMap("headers"),
            sources = sources,
            subtitles = subs,
            thumbnails = thumbUrl,
        )
    }
}

enum class SearchLegStatus { OK, EMPTY, TIMEOUT, ERROR }

data class SearchLeg(
    val provider: ExtProvider,
    val items: List<ExtCard>,
    val status: SearchLegStatus,
) {
    val hasItems: Boolean get() = items.isNotEmpty()
}
