package com.saikou.sozo_tv.data.extensions

import android.content.Context
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.engine.aniyomi.AniyomiHost
import com.saikou.sozo_tv.engine.aniyomi.AniyomiRepoManager
import com.saikou.sozo_tv.engine.cloudstream.PluginHost
import com.saikou.sozo_tv.engine.cloudstream.RepoManager
import com.saikou.sozo_tv.engine.server.ServerHost
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ExtensionEngine(private val appContext: Context = MyApp.context) {

    private val aniyomiHost by lazy { AniyomiHost(appContext) }
    private val aniyomiRepos by lazy { AniyomiRepoManager(appContext, aniyomiHost) }
    private val csHost by lazy { PluginHost(appContext) }
    private val csRepos by lazy { RepoManager(appContext, csHost) }
    private val serverHost by lazy { ServerHost(appContext) }

    private val backends: Map<String, ExtBackend> by lazy {
        mapOf(
            ExtGroup.ANIYOMI to AniyomiBackend(aniyomiHost, aniyomiRepos),
            ExtGroup.CLOUDSTREAM to CloudStreamBackend(csHost, csRepos),
            ExtGroup.SERVER to ServerBackend(serverHost),
        )
    }

    private val prefs by lazy { appContext.getSharedPreferences("ext_engine", Context.MODE_PRIVATE) }

    private val homeMutex = Mutex()
    @Volatile private var homeCache: Pair<String, ExtHome>? = null

    fun getActiveGroup(): String = prefs.getString(KEY_GROUP, ExtGroup.ANIYOMI) ?: ExtGroup.ANIYOMI

    fun getActiveProvider(): String? = prefs.getString(KEY_PROVIDER, null)?.ifEmpty { null }

    fun setActiveProvider(id: String, group: String, name: String? = null) {
        prefs.edit()
            .putString(KEY_PROVIDER, id)
            .putString(KEY_GROUP, group)
            .putString(KEY_PROVIDER_NAME, name?.ifEmpty { null } ?: deriveName(id))
            .apply()
        homeCache = null // switching source invalidates the cached home page
    }

    /** Human-readable name of the active provider (e.g. "AnimeOnsen"), not the sentinel. */
    fun getActiveProviderName(): String? {
        prefs.getString(KEY_PROVIDER_NAME, null)?.ifEmpty { null }?.let { return it }
        return getActiveProvider()?.let { deriveName(it) }
    }

    private fun deriveName(id: String): String = id.substringAfter(':', id)

    private fun providerFor(id: String?): String? = id?.ifEmpty { null } ?: getActiveProvider()

    private fun groupForProvider(provider: String): String = when {
        provider.startsWith("an:") -> ExtGroup.ANIYOMI
        provider.startsWith("cs:") -> ExtGroup.CLOUDSTREAM
        provider.startsWith("sv:") -> ExtGroup.SERVER
        else -> ExtGroup.CLOUDSTREAM
    }

    private fun backendForProvider(provider: String): ExtBackend? = backends[groupForProvider(provider)]

    private fun backendForGroup(group: String): ExtBackend? = backends[group]

    suspend fun ensureLoaded(group: String) = withContext(Dispatchers.IO) {
        backendForGroup(group)?.ensureLoaded()
        Unit
    }

    /**
     * First-launch setup: install the curated default repos for [groups] that aren't already
     * present, then activate the first available provider if none is active yet. Idempotent —
     * a no-op once the defaults are installed and a source is active. [progress] reports
     * (repo name, current, total) while a repo downloads.
     */
    suspend fun ensureDefaultsInstalled(
        groups: List<String> = listOf(ExtGroup.ANIYOMI, ExtGroup.CLOUDSTREAM),
        progress: ((name: String, current: Int, total: Int) -> Unit)? = null,
    ): Unit = withContext(Dispatchers.IO) {
        for (group in groups) {
            val installed = runCatching { listRepos(group).map { it.url }.toSet() }
                .getOrDefault(emptySet())
            ShortcodeRegistry.entries(group)
                .filter { it.url !in installed }
                .forEach { entry ->
                    runCatching {
                        addRepo(group, entry.url) { c, t -> progress?.invoke(entry.name, c, t) }
                    }
                }
        }
        if (getActiveProvider() == null) {
            groups.firstNotNullOfOrNull { group ->
                runCatching { providers(group).firstOrNull() }.getOrNull()
            }?.let { setActiveProvider(it.id, it.group, it.name) }
        }
    }

    /** True once a source has been picked — used to decide whether first-launch setup is needed. */
    fun hasActiveProvider(): Boolean = getActiveProvider() != null

    suspend fun providers(group: String): List<ExtProvider> = withContext(Dispatchers.IO) {
        val b = backendForGroup(group) ?: return@withContext emptyList()
        b.ensureLoaded()
        ExtParser.providers(b.providersJson())
    }

    suspend fun listRepos(group: String): List<ExtRepo> = withContext(Dispatchers.IO) {
        val b = backendForGroup(group) ?: return@withContext emptyList()
        ExtParser.repos(b.listReposJson())
    }

    suspend fun addRepo(group: String, url: String, progress: ((Int, Int) -> Unit)? = null): Int =
        withContext(Dispatchers.IO) {
            val b = backendForGroup(group) ?: return@withContext 0
            val result = b.addRepo(url, progress)
            result.optInt("sourceCount", result.optInt("pluginCount", 0))
        }

    suspend fun removeRepo(group: String, url: String) = withContext(Dispatchers.IO) {
        backendForGroup(group)?.removeRepo(url)
        Unit
    }

    suspend fun checkUpdates(): Int = withContext(Dispatchers.IO) {
        backendForGroup(ExtGroup.CLOUDSTREAM)?.checkUpdates(null)?.optInt("count", 0) ?: 0
    }

    // The home screen fires banner + categories (+ genres) loads at once, each of which
    // calls home(). Without deduping, that's 2-3 independent getMainPage fetches that can
    // disagree (one empty, one not → blank banner / flaky first load). Cache the last
    // non-empty result per provider+page and serialise concurrent fetches so every caller
    // sees the same payload from a single network call.
    suspend fun home(provider: String? = null, page: Int = 1): ExtHome? = withContext(Dispatchers.IO) {
        val p = providerFor(provider) ?: return@withContext null
        val key = "$p:$page"
        homeCache?.let { if (it.first == key) return@withContext it.second }
        homeMutex.withLock {
            homeCache?.let { if (it.first == key) return@withLock it.second }
            val b = backendForProvider(p) ?: return@withLock null
            b.ensureLoaded()
            // Cap the fetch: a provider whose getMainPage hangs (e.g. an auth/region-gated
            // mirror like NetMirror's Hotstar) would otherwise freeze the home forever. On
            // timeout, fall back to empty so the UI shows an error/Retry instead of hanging.
            val home = withTimeoutOrNull(HOME_TIMEOUT_MS) {
                ExtParser.home(b.getMainPageJson(p, page))
            } ?: ExtHome(p, emptyList(), emptyList())
            // Only cache real content so a transient/empty result can still be retried.
            if (home.banner.isNotEmpty() || home.sections.isNotEmpty()) homeCache = key to home
            home
        }
    }

    suspend fun section(provider: String?, slug: String, page: Int = 1): ExtPage? =
        withContext(Dispatchers.IO) {
            val p = providerFor(provider) ?: return@withContext null
            val b = backendForProvider(p) ?: return@withContext null
            b.ensureLoaded()
            ExtParser.page(b.getSectionJson(p, slug, page))
        }

    suspend fun genres(provider: String? = null): List<ExtGenre> = withContext(Dispatchers.IO) {
        val p = providerFor(provider) ?: return@withContext emptyList()
        val b = backendForProvider(p) ?: return@withContext emptyList()
        b.ensureLoaded()
        ExtParser.genres(b.getGenresJson(p))
    }

    suspend fun search(provider: String?, query: String): ExtPage? = withContext(Dispatchers.IO) {
        val p = providerFor(provider) ?: return@withContext null
        val b = backendForProvider(p) ?: return@withContext null
        b.ensureLoaded()
        ExtParser.page(b.searchJson(p, query))
    }

    suspend fun load(provider: String, url: String): ExtDetail? = withContext(Dispatchers.IO) {
        val b = backendForProvider(provider) ?: return@withContext null
        b.ensureLoaded()
        ExtParser.detail(b.loadJson(provider, url))
    }

    suspend fun loadLinks(provider: String, mediaRef: String): ExtMedia = withContext(Dispatchers.IO) {
        val b = backendForProvider(provider)
            ?: return@withContext ExtMedia(null, null, emptyMap(), emptyList(), emptyList())
        b.ensureLoaded()
        ExtParser.media(b.loadLinksJson(provider, mediaRef))
    }

    suspend fun aniyomiConfigurable(provider: String): ConfigurableAnimeSource? =
        withContext(Dispatchers.IO) {
            aniyomiRepos.ensureLoaded()
            aniyomiHost.configurable(provider.removePrefix("an:"))
        }

    companion object {
        private const val KEY_GROUP = "active_group"
        private const val KEY_PROVIDER = "active_provider"
        private const val KEY_PROVIDER_NAME = "active_provider_name"
        private const val HOME_TIMEOUT_MS = 25_000L

        val shared: ExtensionEngine by lazy { ExtensionEngine() }
    }
}
