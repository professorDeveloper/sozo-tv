package com.saikou.sozo_tv.engine.cloudstream

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.app.MyApp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginManager as CsPluginManager
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.nicehttp.ignoreAllSSLErrors
import dalvik.system.PathClassLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.TorrentLoadResponse
import kotlin.uuid.ExperimentalUuidApi

/**
 * CloudStream plugin host (Android-only).
 *
 * Loads `.cs3` plugins like CloudStream's PluginManager (PathClassLoader(file,
 * appContext.classLoader) → manifest.json → loadClass(pluginClassName) →
 * newInstance → load(context) → registerMainAPI → APIHolder.allProviders) and
 * exposes the provider surface as JSON for the Flutter MethodChannel bridge.
 *
 * Runtime comes from `com.github.recloudstream.cloudstream:library` (MainAPI/
 * APIHolder/app HTTP/extractors/BasePlugin). `Plugin` (app-module class the
 * plugins subclass) is re-declared locally in com.lagradost.cloudstream3.plugins.
 * See docs/CLOUDSTREAM_INTEGRATION.md.
 *
 * Output shapes mirror the existing soplay models so the Flutter side maps onto
 * its current entities (provider card / detail+episodes / VideoSourceEntity /
 * subtitle). Stable id scheme `cs:<MainAPI.name>` + contentUrl=load-url so
 * my-list / continue-watching keep working.
 */
class PluginHost(private val appContext: Context) {

    companion object {
        private const val TAG = "CloudStreamHost"
        private const val UNKNOWN_QUALITY = 400
        private const val LINK_CACHE_MS = 20 * 60 * 1000L
        private const val LINK_CACHE_ENTRIES = 40
        private const val GOOD_LINK_GRACE_MS = 2500L
        private const val ANY_LINK_GRACE_MS = 6000L
        private const val WAIT_CAP_MS = 90_000L
        private const val COLLECT_CAP_MS = 120_000L
    }

    init {
        // Lenient TLS for CloudStream's own client, as the CloudStream app does: plugins scrape
        // arbitrary hosts with broken chains, and this client never carries a Sozo token.
        runCatching {
            app.baseClient = app.baseClient.newBuilder().dns(com.saikou.sozo_tv.engine.network.ExtensionDns.dns).ignoreAllSSLErrors().build()
        }
    }

    private val loaded = java.util.concurrent.ConcurrentHashMap<String, BasePlugin>()
    // Plugins that threw during load(). Remember them: without this, every apiByName() retries
    // the DexClassLoader and reprints the stack trace.
    private val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    // internalName -> why it failed, in one line the Sources screen can show. See loadCs3.
    private val lastErrors = java.util.concurrent.ConcurrentHashMap<String, String>()
    // internalName -> the MainAPI provider names it registered (for unload + dedup).
    private val pluginProviders = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    // MainAPI.name -> plugin iconUrl (from plugins.json) for nicer provider icons.
    private val providerIcons = java.util.concurrent.ConcurrentHashMap<String, String>()

    // Lazy-load registry: providerName -> where to load it from. Populated cheaply
    // on startup (no DexClassLoader) so the provider list is instant; the actual
    // .cs3 is loaded only when that provider is first used.
    data class Meta(
        val provider: String, val icon: String?, val internalName: String,
        val cs3Path: String, val repo: String? = null,
        val lang: String? = null, val nsfw: Boolean = false,
    )
    private val metas = java.util.Collections.synchronizedMap(LinkedHashMap<String, Meta>())

    /** Register provider metadata WITHOUT loading the plugin (startup path). */
    fun registerMeta(
        provider: String, icon: String?, internalName: String,
        cs3Path: String, repo: String? = null, lang: String? = null, nsfw: Boolean = false,
    ) {
        metas[provider] = Meta(provider, icon, internalName, cs3Path, repo, lang, nsfw)
        if (!icon.isNullOrEmpty()) providerIcons[provider] = icon
    }

    /** Ensure the plugin backing a provider is loaded (lazy, on first use). */
    private fun ensurePluginLoaded(provider: String) {
        val m = metas[provider] ?: return
        if (loaded.containsKey(m.internalName)) return
        val f = File(m.cs3Path)
        if (f.exists()) loadCs3(f, m.internalName, m.icon, m.repo, m.lang, m.nsfw)
    }

    private data class Manifest(
        val name: String?, val pluginClassName: String?,
        val version: Int?, val requiresResources: Boolean,
    )

    private fun readManifest(loader: ClassLoader): Manifest? {
        val stream = loader.getResourceAsStream("manifest.json") ?: return null
        val text = stream.bufferedReader().use { it.readText() }
        val o = JSONObject(text)
        return Manifest(
            name = o.optString("name").ifEmpty { null },
            pluginClassName = o.optString("pluginClassName").ifEmpty { null },
            version = if (o.has("version")) o.optInt("version") else null,
            requiresResources = o.optBoolean("requiresResources", false),
        )
    }

    /**
     * The context a plugin's `load()` receives. CloudStream's own PluginManager passes the
     * foreground `AppCompatActivity`, and plugins such as Aniworld/Jellyfin/ShowBox cast the
     * argument to it directly — handing them the Application throws ClassCastException. The
     * app context is only a fallback for the window where no activity is resumed.
     */
    private fun pluginLoadContext(): Context =
        MyApp.currentActivity as? AppCompatActivity ?: appContext

    /**
     * Load a downloaded .cs3; returns the provider names it registered.
     *
     * Serialised, and deliberately so. Two things here are not thread-safe and
     * both were being run concurrently:
     *
     *  - the `loaded[internalName]` guard is a check-then-act, and the value is
     *    only published AFTER `load()` returns. Two callers both passed it and
     *    both registered the plugin, which is where
     *    `providers=[Netflix, Prime Video, Netflix, Prime Video, ...]` came from.
     *  - the before/after diff over `APIHolder.allProviders` is GLOBAL. With two
     *    plugins loading at once, one plugin's providers land inside the other's
     *    `added` list, so `metas`/`providerIcons` get attributed to the wrong
     *    plugin and removing a repo takes the wrong providers with it.
     *
     * Plugin loading is a handful of seconds once per process, so a lock costs
     * nothing that matters and is the only way that diff can be correct.
     */
    fun loadCs3(file: File, internalName: String, iconUrl: String? = null, repo: String? = null, lang: String? = null, nsfw: Boolean = false): List<String> {
        // Already loaded this process → don't register twice (avoids duplicates).
        loaded[internalName]?.let { return pluginProviders[internalName] ?: emptyList() }
        if (internalName in failed) return emptyList()
        synchronized(loadLock) {
        // Re-checked under the lock: the fast path above is what both racers pass.
        loaded[internalName]?.let { return pluginProviders[internalName] ?: emptyList() }
        if (internalName in failed) return emptyList()
        return try {
            try { file.setReadOnly() } catch (_: Throwable) {}
            val loader = PathClassLoader(file.absolutePath, appContext.classLoader)
            val manifest = readManifest(loader) ?: run {
                Log.e(TAG, "no manifest.json in ${file.name}"); return emptyList()
            }
            val className = manifest.pluginClassName ?: run {
                Log.e(TAG, "no pluginClassName in ${file.name}"); return emptyList()
            }
            // The library ships allProviders as a synchronized list and documents that
            // iterating it must hold its monitor — a plugin registering from its own
            // thread mid-iteration is a ConcurrentModificationException otherwise.
            val before = synchronized(APIHolder.allProviders) {
                APIHolder.allProviders.map { it.name }.toSet()
            }

            val instance = loader.loadClass(className)
                .getDeclaredConstructor().newInstance() as BasePlugin
            instance.filename = file.absolutePath

            if (manifest.requiresResources) {
                val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                    .invoke(assets, file.absolutePath)
                @Suppress("DEPRECATION")
                (instance as? Plugin)?.resources = Resources(
                    assets, appContext.resources.displayMetrics, appContext.resources.configuration
                )
            }
            if (instance is Plugin) instance.load(pluginLoadContext()) else instance.load()
            loaded[internalName] = instance
            // So a plugin walking PluginManager.getPluginsOnline() to find its own .cs3 — the
            // usual reason to call it — gets a real answer.
            CsPluginManager.record(internalName, file.absolutePath)

            val added = synchronized(APIHolder.allProviders) {
                APIHolder.allProviders.map { it.name }
            }.filter { it !in before }
            pluginProviders[internalName] = added
            added.forEach { name ->
                val api = synchronized(APIHolder.allProviders) { APIHolder.allProviders.first { it.name == name } }
                metas[name] = Meta(name, iconUrl, internalName, file.absolutePath, repo, lang?.takeIf { it.isNotBlank() } ?: api.lang, nsfw || api.supportedTypes.any { it.name.equals("NSFW", true) })
            }
            if (!iconUrl.isNullOrEmpty()) added.forEach { providerIcons[it] = iconUrl }
            Log.i(TAG, "loaded ${file.name}: providers=$added")
            added
        } catch (t: Throwable) {
            failed.add(internalName)
            // Kept, not only logged. A failure here used to become an empty provider list and a
            // line in logcat: the .cs3 downloaded, registered nothing, and never appeared in the
            // Sources screen, which from the sofa is indistinguishable from a repo that simply
            // does not carry that source. The Sources screen reads [lastErrorsJson] back so the
            // plugin is named along with what went wrong.
            lastErrors[internalName] = describeLoadFailure(t)
            Log.e(TAG, "failed to load ${file.name}: ${Log.getStackTraceString(t)}")
            emptyList()
        }
        }
    }

    /** Guards the load + provider-diff critical section. See [loadCs3]. */
    private val loadLock = Any()

    /**
     * Swap a loaded plugin for a newer .cs3.
     *
     * [loadCs3] hands back the instance already loaded in this process, so an update downloaded
     * the new version and went on running the old code until the app was restarted. The old
     * plugin's providers are taken out of the registry first so the new load registers — and
     * reports — them again.
     */
    fun reloadCs3(file: File, internalName: String, iconUrl: String? = null, repo: String? = null, lang: String? = null, nsfw: Boolean = false): List<String> {
        synchronized(loadLock) {
            val old = pluginProviders[internalName].orEmpty().toSet()
            if (old.isNotEmpty()) {
                runCatching {
                    synchronized(APIHolder.allProviders) {
                        APIHolder.allProviders.removeAll { it.name in old }
                    }
                }
            }
            synchronized(linkCollections) {
                linkCollections.keys.filter { key -> old.any { key.startsWith("$it\u0000") } }.forEach {
                    linkCollections.remove(it)?.job?.cancel()
                }
            }
            loaded.remove(internalName)
            pluginProviders.remove(internalName)
            failed.remove(internalName)
            lastErrors.remove(internalName)
            CsPluginManager.forget(internalName)
        }
        return loadCs3(file, internalName, iconUrl, repo, lang, nsfw)
    }

    /** Every plugin that failed to load this session: internalName -> one-line reason. */
    fun lastErrorsJson(): String = JSONObject(lastErrors as Map<*, *>).toString()

    /**
     * A one-line reason somebody can act on.
     *
     * A stack trace is the right thing in logcat and the wrong thing on a screen ten feet away.
     * These three cases are nearly all of them in practice and each asks something different of
     * the reader: a plugin this app cannot host, a plugin newer than the CloudStream runtime it
     * bundles, and a broken download.
     */
    private fun describeLoadFailure(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        val detail = root.message?.trim().orEmpty().ifEmpty { root.javaClass.simpleName }
        return when (root) {
            is NoClassDefFoundError, is ClassNotFoundException ->
                appContext.getString(R.string.plugin_error_missing_class, detail)
            is NoSuchMethodError, is NoSuchFieldError, is AbstractMethodError ->
                appContext.getString(R.string.plugin_error_newer_cloudstream, detail)
            // A truncated or corrupt .cs3 arrives as a ZipException, which is an IOException.
            is java.io.IOException ->
                appContext.getString(R.string.plugin_error_unreadable)
            else ->
                appContext.getString(R.string.plugin_error_other, root.javaClass.simpleName, detail)
        }
    }

    /** Remove providers by name (loaded or lazy) — used when a repo is removed. */
    fun removeProviders(names: List<String>) {
        if (names.isEmpty()) return
        val set = names.toSet()
        try {
            synchronized(APIHolder.allProviders) {
                APIHolder.allProviders.removeAll { it.name in set }
            }
        } catch (_: Throwable) {}
        names.forEach { providerIcons.remove(it); metas.remove(it) }
        // Drop any loaded plugins whose providers are now all gone.
        val internalNames = pluginProviders.filterValues { it.any { n -> n in set } }.keys.toList()
        internalNames.forEach {
            loaded.remove(it); pluginProviders.remove(it); CsPluginManager.forget(it)
        }
        Log.i(TAG, "removed providers=$names")
    }

    private fun apiByName(name: String): MainAPI? {
        val n = name.removePrefix("cs:")
        ensurePluginLoaded(n)
        return synchronized(APIHolder.allProviders) {
            APIHolder.allProviders.firstOrNull { it.name == n }
        }
    }

    private fun slugify(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    /** Provider list for Flutter — from lazy metadata (no plugins loaded yet). */
    fun providersJson(): String {
        val arr = JSONArray()
        for (m in synchronized(metas) { metas.values.toList() }) {
            arr.put(JSONObject().apply {
                put("id", "cs:${m.provider}")
                put("name", m.provider)
                put("lang", m.lang)
                put("nsfw", m.nsfw)
                m.icon?.let { put("icon", it) }
                m.repo?.let { if (it.isNotEmpty()) put("repo", it) }
            })
        }
        return arr.toString()
    }

    fun providerMetadata(name: String): JSONObject = metas[name]?.let {
        JSONObject().put("lang", it.lang).put("nsfw", it.nsfw)
    } ?: JSONObject()

    /**
     * "Genres" for the home chip row = the provider's MainPageData categories.
     * `slug` carries the MainPageData.data so a tap re-uses getSectionJson (the
     * same path as a section's view-all).
     */
    fun getGenresJson(providerName: String): String {
        val api = apiByName(providerName)
        val arr = JSONArray()
        if (api != null) {
            for (mp in api.mainPage) {
                if (mp.name.isBlank()) continue
                arr.put(JSONObject().apply {
                    put("provider", "cs:${api.name}")
                    put("name", mp.name)
                    put("slug", mp.data) // getSection(data) resolves this category
                    put("image", "")
                })
            }
        }
        return arr.toString()
    }

    private fun cardJson(r: SearchResponse, apiName: String) = JSONObject().apply {
        put("provider", "cs:$apiName")
        put("externalId", r.url)
        put("title", r.name)
        put("slug", r.url)
        put("contentUrl", r.url)
        put("thumbnail", r.posterUrl)
        put("type", r.type?.name)
    }

    suspend fun getMainPageJson(providerName: String, page: Int): String {
        val api = apiByName(providerName) ?: run {
            Log.e(TAG, "getMainPage: provider '$providerName' not found (loaded=${APIHolder.allProviders.size})")
            return JSONObject(mapOf("provider" to providerName, "banner" to JSONArray(), "sections" to JSONArray())).toString()
        }
        val sections = JSONArray()
        val banner = JSONArray()
        if (api.mainPage.isEmpty()) {
            Log.w(TAG, "getMainPage ${api.name}: provider has no mainPage list")
            return JSONObject(mapOf("provider" to "cs:${api.name}", "banner" to banner, "sections" to sections)).toString()
        }
        // Home shows at most a handful of rows; capping the sections also caps the
        // number of getMainPage network calls we make per provider (perf).
        val maxSections = 5
        for (mp in api.mainPage) {
            if (sections.length() >= maxSections) break
            try {
                val resp = api.getMainPage(page, MainPageRequest(mp.name, mp.data, false))
                if (resp == null) { Log.w(TAG, "getMainPage ${api.name}: '${mp.name}' returned null"); continue }
                for (list in resp.items) {
                    if (sections.length() >= maxSections) break
                    val items = JSONArray()
                    for (sr in list.list) items.put(cardJson(sr, api.name))
                    if (items.length() == 0) continue
                    if (banner.length() == 0) {
                        var i = 0
                        while (i < items.length() && i < 12) { banner.put(items.get(i)); i++ }
                    }
                    val key = slugify(list.name)
                    sections.put(JSONObject().apply {
                        put("key", key)
                        put("label", list.name)
                        // slug = the MainPageData.data so view-all can re-request
                        // exactly this section (paginated) via getSectionJson.
                        put("viewAll", JSONObject().apply { put("type", "cs"); put("slug", mp.data) })
                        put("items", items)
                    })
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                // Log (don't swallow) — most empty-home failures are a plugin
                // referencing an app-module class our embedded `library` lacks
                // (NoClassDefFoundError), surfaced here per section.
                Log.e(TAG, "getMainPage ${api.name} '${mp.name}': ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        Log.i(TAG, "getMainPage ${api.name}: ${sections.length()} sections, ${banner.length()} banner")
        return JSONObject().apply {
            put("provider", "cs:${api.name}"); put("banner", banner); put("sections", sections)
        }.toString()
    }

    /** View-all for one home section: re-request just that MainPageData, paginated. */
    suspend fun getSectionJson(providerName: String, data: String, page: Int): String {
        val api = apiByName(providerName)
        val items = JSONArray()
        if (api != null) {
            val mp = api.mainPage.firstOrNull { it.data == data }
            val name = mp?.name ?: data
            try {
                val resp = api.getMainPage(page, MainPageRequest(name, data, false))
                if (resp != null) {
                    for (list in resp.items) for (sr in list.list) items.put(cardJson(sr, api.name))
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                throw IllegalStateException("${api.name}: could not load this section", t)
            }
        }
        return JSONObject().apply {
            put("provider", providerName)
            put("items", items)
            put("page", page)
            put("totalPages", if (items.length() > 0) page + 1 else page)
        }.toString()
    }

    suspend fun searchJson(providerName: String, query: String): String {
        val api = apiByName(providerName)
            ?: error("CloudStream provider '$providerName' is unavailable")
        val result = searchCloudStream(api, query)
        val items = JSONArray()
        result?.items?.forEach { items.put(cardJson(it, api.name)) }
        return JSONObject().apply {
            put("provider", providerName)
            put("items", items)
            put("query", query)
            put("page", 1)
            put("totalPages", if (result?.hasNext == true) 2 else 1)
        }.toString()
    }

    private fun formatDate(raw: Long): String? = try {
        val ms = if (raw < 10_000_000_000L) raw * 1000 else raw
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(ms))
    } catch (_: Throwable) { null }

    private fun episodeJson(e: Episode, index: Int) = JSONObject().apply {
        put("episode", e.episode ?: (index + 1))
        put("label", e.name ?: "Episode ${e.episode ?: (index + 1)}")
        put("mediaRef", e.data)                 // → loadLinks(data)
        if (e.posterUrl != null) put("image", e.posterUrl)   // preview thumbnail
        if (e.season != null) put("season", e.season)
        if (!e.description.isNullOrEmpty()) put("overview", e.description)
        e.date?.let { d -> formatDate(d)?.let { put("airdate", it) } }
        e.runTime?.let { if (it > 0) put("runtime", "$it min") }
    }

    suspend fun loadJson(providerName: String, url: String): String {
        val api = apiByName(providerName) ?: error("CloudStream provider '$providerName' is unavailable")
        val resp = api.load(url) ?: error("${api.name}: the provider returned no details")
        val episodes = JSONArray()
        var isSerial = false
        when (resp) {
            is TvSeriesLoadResponse -> {
                isSerial = true
                resp.episodes.forEachIndexed { i, e -> episodes.put(episodeJson(e, i)) }
            }
            is AnimeLoadResponse -> {
                isSerial = true
                val list = resp.episodes.values.maxByOrNull { it.size } ?: emptyList()
                list.forEachIndexed { i, e -> episodes.put(episodeJson(e, i)) }
            }
            is MovieLoadResponse -> {
                // dataUrl is what loadLinks() expects; fall back to the page url
                // if a provider leaves it empty.
                val ref = resp.dataUrl.ifEmpty { resp.url }
                episodes.put(JSONObject().apply {
                    put("episode", 1); put("label", "Play"); put("mediaRef", ref)
                })
            }
            is LiveStreamLoadResponse -> {
                episodes.put(JSONObject().apply {
                    put("episode", 1); put("label", "Watch live")
                    put("mediaRef", resp.dataUrl.ifEmpty { resp.url })
                })
            }
            is TorrentLoadResponse -> {
                val ref = resp.magnet ?: resp.torrent
                require(!ref.isNullOrBlank()) { "This torrent entry has no magnet or torrent link" }
                episodes.put(JSONObject().apply {
                    put("episode", 1); put("label", "Play"); put("mediaRef", ref)
                })
            }
        }
        val syncIds = JSONObject()
        runCatching {
            resp.syncData.forEach { (name, value) ->
                val id = value.trim().toIntOrNull() ?: return@forEach
                if (id > 0) syncIds.put(name.lowercase(), id)
            }
        }

        // Cast (actors) + related (recommendations) when the provider supplies them.
        val cast = JSONArray()
        (resp.actors ?: emptyList()).forEach { ad ->
            cast.put(JSONObject().apply {
                put("name", ad.actor.name)
                if (!ad.actor.image.isNullOrEmpty()) put("image", ad.actor.image)
                if (!ad.roleString.isNullOrEmpty()) put("character", ad.roleString)
            })
        }
        val related = JSONArray()
        (resp.recommendations ?: emptyList()).forEach { sr -> related.put(cardJson(sr, api.name)) }
        // Fallback: many providers leave recommendations empty — derive "similar"
        // from a title search so the section isn't blank.
        if (related.length() == 0) {
            try {
                val q = resp.name.replace(Regex("\\(.*?\\)"), "").trim()
                if (q.length >= 2) {
                    val results = searchCloudStream(api, q)?.items.orEmpty()
                    for (sr in results) {
                        if (sr.url == resp.url) continue
                        related.put(cardJson(sr, api.name))
                        if (related.length() >= 20) break
                    }
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (_: Throwable) { }
        }

        return JSONObject().apply {
            put("provider", "cs:${api.name}")
            put("contentId", resp.url); put("contentUrl", resp.url)
            put("title", resp.name); put("description", resp.plot)
            put("thumbnail", resp.posterUrl); put("banner", resp.backgroundPosterUrl)
            put("year", resp.year ?: JSONObject.NULL)
            resp.duration?.let { if (it > 0) put("duration", "$it min") }
            put("genres", JSONArray(resp.tags ?: emptyList<String>()))
            put("type", resp.type.name)
            put("isSerial", isSerial)
            if (syncIds.length() > 0) put("syncIds", syncIds)
            put("cast", cast)
            put("related", related)
            put("episodes", episodes)
        }.toString()
    }

    /** The library's shared client, the one plugins' own requests go through. */
    private fun libraryClient(): okhttp3.OkHttpClient? = try {
        val requests = Class.forName("com.lagradost.cloudstream3.MainActivityKt")
            .getDeclaredMethod("getApp").invoke(null)
        requests?.javaClass?.methods
            ?.firstOrNull { it.name == "getBaseClient" && it.parameterTypes.isEmpty() }
            ?.invoke(requests) as? okhttp3.OkHttpClient
    } catch (_: Throwable) {
        null
    }

    /**
     * One episode's links, gathered as the plugin produces them.
     *
     * Extractors call back as they finish, and a plugin that tries a WebView
     * sniff last can take a minute to return. Waiting for the whole call is
     * what made CloudStream episodes sit on a spinner long after a playable
     * link had arrived — CloudStream's own player starts on the first good link
     * and keeps collecting. So the collection runs on its own, and the call
     * that asked for it returns as soon as there is something worth playing.
     */
    private class LinkCollection(val startedAt: Long) {
        var job: kotlinx.coroutines.Job? = null
        val sources = ArrayList<Pair<Int, JSONObject>>()
        val subs = ArrayList<com.lagradost.cloudstream3.SubtitleFile>()
        val seenUrls = HashSet<String>()
        val seenSubs = HashSet<String>()
        var firstLinkAt = 0L
        var bestRank = -1
        var interceptorToken: String? = null
        @Volatile var done = false
        @Volatile var error: String? = null
    }

    private val linkScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )
    private val linkCollections = LinkedHashMap<String, LinkCollection>()

    // AudioFile is @Prerelease in the CloudStream library; the annotation is an
    // IDE hint with BINARY retention, so reading the list a plugin may have filled
    // costs nothing at runtime and is empty for every plugin that sets none.
    @OptIn(com.lagradost.cloudstream3.Prerelease::class)
    suspend fun loadLinksJson(providerName: String, data: String): String {
        if (data.startsWith("magnet:", true) || data.substringBefore('?').endsWith(".torrent", true)) {
            return JSONObject().put("videoSources", JSONArray().put(JSONObject()
                .put("videoUrl", data).put("type", "torrent").put("quality", "Torrent")
                .put("isDefault", true))).put("subtitles", JSONArray()).toString()
        }
        val api = apiByName(providerName)
            ?: return JSONObject().apply {
                put("videoSources", JSONArray())
                put("subtitles", JSONArray())
                put("error", "$providerName: " +
                    (metas[providerName.removePrefix("cs:")]?.internalName?.let { lastErrors[it] } ?: "provider not loaded: $providerName"))
            }.toString()

        // Reopening an episode, a retry, the next visit within twenty minutes:
        // the links are already known, as CloudStream's own player keeps them.
        val key = "${api.name}\u0000$data"
        val now = System.currentTimeMillis()
        val collection = synchronized(linkCollections) {
            linkCollections[key]?.takeIf {
                now - it.startedAt < LINK_CACHE_MS && synchronized(it) { !it.done || it.sources.isNotEmpty() }
            } ?: LinkCollection(now).also {
                linkCollections[key] = it
                while (linkCollections.size > LINK_CACHE_ENTRIES) {
                    linkCollections.remove(linkCollections.keys.first())?.job?.cancel()
                }
                startCollecting(api, data, it)
            }
        }

        // Wait for the first good link rather than for the last extractor:
        // once a high or adaptive link is in, a short grace lets a better one
        // that is about to land win; a lesser one gets a little longer; and the
        // whole wait is bounded, because a plugin that never returns used to
        // leave the player on a spinner for good.
        while (true) {
            val (done, count, firstAt, best) = synchronized(collection) {
                listOf(
                    if (collection.done) 1L else 0L,
                    collection.sources.size.toLong(),
                    collection.firstLinkAt,
                    collection.bestRank.toLong(),
                )
            }
            if (done == 1L) break
            val t = System.currentTimeMillis()
            if (count > 0) {
                val since = t - firstAt
                if (best >= 1000 && since >= GOOD_LINK_GRACE_MS) break
                if (since >= ANY_LINK_GRACE_MS) break
            }
            if (t - collection.startedAt >= WAIT_CAP_MS) break
            kotlinx.coroutines.delay(150)
        }
        return buildLinksJson(api, collection)
    }

    @OptIn(com.lagradost.cloudstream3.Prerelease::class)
    private fun startCollecting(api: MainAPI, data: String, c: LinkCollection) {
        c.job = linkScope.launch {
            try {
                kotlinx.coroutines.withTimeoutOrNull(COLLECT_CAP_MS) {
                    api.loadLinks(
                        data = data,
                        isCasting = false,
                        subtitleCallback = { sf ->
                            synchronized(c) {
                                if (sf.url.isNotEmpty() && c.seenSubs.add(sf.url)) c.subs.add(sf)
                            }
                        },
                        callback = { link -> collectLink(api, link, c) },
                    )
                } ?: run { c.error = "timed out after ${COLLECT_CAP_MS / 1000}s" }
            } catch (t: Throwable) {
                c.error = "${t.javaClass.simpleName}: ${t.message ?: "failed"}"
                Log.e(TAG, "loadLinks ${api.name}", t)
            } finally {
                c.done = true
                // With the elapsed time, because the two ways of getting zero
                // look identical in a log and nothing like each other in cause:
                // a fast empty answer is a title with no mirrors, and a
                // sixty-second one is WebViewResolver polling out its timeout.
                Log.i(
                    TAG,
                    "loadLinks ${api.name}: ${c.sources.size} source(s), " +
                        "${c.subs.size} sub(s) in ${System.currentTimeMillis() - c.startedAt}ms",
                )
            }
        }
    }

    @OptIn(com.lagradost.cloudstream3.Prerelease::class)
    private fun collectLink(api: MainAPI, link: ExtractorLink, c: LinkCollection) {
        // Torrents and magnets are passed through tagged as "torrent": the app
        // embeds a torrent server and the player turns such a link into a local
        // HTTP stream (see core/torrent/ and player_page.media.dart).
        //
        // Relative urls ("dl.php?id=…") are still dropped — ExoPlayer resolves
        // those as a local file path. So is a multi-part ExtractorLinkPlayList,
        // whose url is empty: its parts are separate files the player cannot
        // join, and offering only the first part would stop mid-film.
        val isTorrent = link.type == ExtractorLinkType.TORRENT ||
            link.type == ExtractorLinkType.MAGNET
        val addressable = link.url.startsWith("http", ignoreCase = true) ||
            (isTorrent && link.url.startsWith("magnet:", ignoreCase = true))
        if (!addressable) return
        synchronized(c) { if (!c.seenUrls.add(link.url)) return }

        // getAllHeaders() folds in the referer exactly the way CloudStream's
        // own player does. The User-Agent it falls back to is the library's,
        // not ours: the extractor fetched the page, and often a signed URL,
        // under that agent, and a CDN that binds its token to the agent 403s
        // the stream when the player presents a different one.
        val headers = LinkedHashMap(link.getAllHeaders())
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            headers["User-Agent"] = com.lagradost.cloudstream3.USER_AGENT
        }

        // A plugin that needs to touch every request — cookies on segments,
        // decrypting subtitles — says so through getVideoInterceptor, and the
        // stream then plays through [CsStreamProxy], which applies it.
        var videoUrl = link.url
        var proxied = false
        var streamToken: String? = null
        if (!isTorrent) {
            val interceptor = try { api.getVideoInterceptor(link) } catch (_: Throwable) { null }
            val base = if (interceptor != null) libraryClient() else null
            if (interceptor != null && base != null) {
                val token = CsStreamProxy.register(base, interceptor, headers)
                val url = token?.let { CsStreamProxy.urlFor(it, link.url) }
                if (token != null && url != null) {
                    videoUrl = url
                    streamToken = token
                    proxied = true
                    synchronized(c) { if (c.interceptorToken == null) c.interceptorToken = token }
                }
            }
        }

        // quality is a resolution int (e.g. 1080) or a Qualities sentinel.
        // Qualities.Unknown is 400, and it is what every link built without a
        // quality carries — most HLS masters. Read as a resolution it labelled
        // them "400p" and sorted an adaptive stream below a 480p file.
        val q = link.quality
        val known = CloudStreamLinkQuality.isKnown(q)
        val res = when {
            !known -> null
            q >= 2160 -> "4K"
            else -> "${q}p"
        }
        val nm = link.name.ifBlank { "Source" }
        val label = if (res != null) "$nm · $res" else nm
        val path = link.url.substringBefore('?').lowercase()
        val type = when {
            link.type == ExtractorLinkType.M3U8 -> "hls"
            link.type == ExtractorLinkType.DASH -> "dash"
            isTorrent -> "torrent"
            // An old-style link built without a type still says what it is.
            path.endsWith(".m3u8") -> "hls"
            path.endsWith(".mpd") -> "dash"
            else -> "http"
        }
        // An adaptive master of unknown height usually tops out at 1080p:
        // ahead of a fixed 720p file, behind a stated 1080p.
        val rank = CloudStreamLinkQuality.rank(q, type)

        val json = JSONObject().apply {
            put("quality", label)
            put("videoUrl", videoUrl)
            put("type", type)
            put("host", link.name)
            put("accessible", true)
            // The proxy sends the plugin's headers itself.
            put("headers", if (proxied) JSONObject() else JSONObject(headers as Map<*, *>))
            if (proxied) put("useLocalProxy", false)
            drmJson(link, headers)?.let { put("drm", it) }
            // Separate audio renditions the extractor says belong with this
            // video; a dual-audio release puts its dub here rather than in the
            // manifest.
            if (link.audioTracks.isNotEmpty()) {
                put("audioTracks", JSONArray().apply {
                    link.audioTracks.forEach { a ->
                        put(JSONObject().apply {
                            put("url", streamToken?.let { CsStreamProxy.urlFor(it, a.url, a.headers.orEmpty()) } ?: a.url)
                            a.headers?.takeIf { it.isNotEmpty() }?.let {
                                put("headers", JSONObject(it as Map<*, *>))
                            }
                        })
                    }
                })
            }
        }
        synchronized(c) {
            c.sources.add(rank to json)
            if (c.firstLinkAt == 0L) c.firstLinkAt = System.currentTimeMillis()
            if (rank > c.bestRank) c.bestRank = rank
        }
    }

    /**
     * A DrmExtractorLink in the shape the player's DrmConfig reads.
     *
     * The player already plays ClearKey and Widevine channels; CloudStream
     * links that carried their keys were handed over as plain streams and
     * decoded to a black screen. ClearKey material arrives as base64url, the
     * EME form, and the player takes hex, which is how keys are published.
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun drmJson(link: ExtractorLink, headers: Map<String, String>): JSONObject? {
        val drm = link as? com.lagradost.cloudstream3.utils.DrmExtractorLink ?: return null
        val scheme = when (drm.uuid.toString().lowercase()) {
            "e2719d58-a985-b3c9-781a-b030af78d30e", "1077efec-c0b2-4d02-ace3-3c1e52e2fb4b" -> "clearkey"
            "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed" -> "widevine"
            "9a04f079-9840-4286-ab92-e65be0885f95" -> "playready"
            else -> return null
        }
        return JSONObject().apply {
            put("scheme", scheme)
            put("licenseUrl", drm.licenseUrl ?: "")
            put("licenseHeaders", JSONObject(headers as Map<*, *>))
            if (scheme == "clearkey") {
                val kid = drm.kid?.let(::b64UrlToHex)
                val k = drm.key?.let(::b64UrlToHex)
                if (kid != null && k != null) put("clearKeys", JSONObject().apply { put(kid, k) })
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun htmlText(s: String): String =
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            android.text.Html.fromHtml(s).toString()
        }

    private fun b64UrlToHex(value: String): String? {
        val v = value.trim()
        if (v.matches(Regex("^[0-9a-fA-F]{32}$"))) return v.lowercase()
        return try {
            android.util.Base64.decode(
                v, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
            ).joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildLinksJson(api: MainAPI, c: LinkCollection): String {
        val (sources, subsSnapshot, token, done, error) = synchronized(c) {
            listOf(ArrayList(c.sources), ArrayList(c.subs), c.interceptorToken, c.done, c.error)
        }
        @Suppress("UNCHECKED_CAST")
        val collected = sources as ArrayList<Pair<Int, JSONObject>>
        @Suppress("UNCHECKED_CAST")
        val subFiles = subsSnapshot as ArrayList<com.lagradost.cloudstream3.SubtitleFile>
        collected.sortByDescending { it.first }
        val videoSources = JSONArray()
        collected.forEachIndexed { i, entry ->
            val o = JSONObject(entry.second.toString())
            o.put("isDefault", i == 0)
            videoSources.put(o)
        }

        val subs = JSONArray()
        val nameCounts = HashMap<String, Int>()
        for (sf in subFiles) {
            val base = htmlText(sf.lang).trim().ifEmpty { "Subtitle" }
            val n = (nameCounts[base] ?: 0) + 1
            nameCounts[base] = n
            val subHeaders = LinkedHashMap(sf.headers ?: emptyMap())
            if (subHeaders.isNotEmpty() && subHeaders.keys.none { it.equals("User-Agent", true) }) {
                subHeaders["User-Agent"] = com.lagradost.cloudstream3.USER_AGENT
            }
            val proxiedUrl = (token as String?)?.let { CsStreamProxy.urlFor(it, sf.url, subHeaders) }
            subs.put(JSONObject().apply {
                put("label", if (n == 1) base else "$base $n")
                put("file", proxiedUrl ?: sf.url)
                put("default", false)
                if (proxiedUrl == null && subHeaders.isNotEmpty()) {
                    put("headers", JSONObject(subHeaders as Map<*, *>))
                }
            })
        }

        val first = if (videoSources.length() > 0) videoSources.getJSONObject(0) else null
        return JSONObject().apply {
            put("videoUrl", first?.optString("videoUrl"))
            put("type", first?.optString("type"))
            put("headers", first?.optJSONObject("headers") ?: JSONObject())
            put("videoSources", videoSources)
            put("subtitles", subs)
            if (done != true) put("partial", true)
            if (videoSources.length() == 0) {
                put("error", "${api.name}: " +
                    ((error as String?) ?: "the provider returned no mirrors for this episode"))
            }
        }.toString()
    }
}
