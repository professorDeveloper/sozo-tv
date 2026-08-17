package com.saikou.sozo_tv.data.extensions

import android.content.Context
import android.util.Log
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.engine.aniyomi.AniyomiHost
import com.saikou.sozo_tv.engine.aniyomi.AniyomiRepoManager
import com.saikou.sozo_tv.engine.cloudstream.PluginHost
import com.saikou.sozo_tv.engine.cloudstream.RepoManager
import com.saikou.sozo_tv.engine.server.ServerHost
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    // First-launch repo installation must survive the splash finishing — see installDefaultsAsync.
    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var defaultsInstall: Deferred<Unit>? = null

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
        groups: List<String> = listOf(ExtGroup.SERVER, ExtGroup.ANIYOMI, ExtGroup.CLOUDSTREAM),
        progress: ((name: String, current: Int, total: Int) -> Unit)? = null,
    ): Unit = withContext(Dispatchers.IO) {
        // Activate a source as early as possible so Home has content instantly. Our own server
        // catalog (SERVER) needs no repo download — it comes straight from /contents/providers —
        // so trying it first gives a working Home immediately (like soplay), before the heavier
        // CloudStream/Aniyomi repos finish installing.
        if (getActiveProvider() == null) {
            groups.firstNotNullOfOrNull { group ->
                runCatching { providers(group).firstOrNull() }.getOrNull()
            }?.let { setActiveProvider(it.id, it.group, it.name) }
        }
        // Install the curated default repos for any repo-backed group missing them. SERVER has
        // no repos (empty ShortcodeRegistry entries) so it's a no-op there.
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
        // Fallback: if the server was unreachable, activate the first provider that did install.
        if (getActiveProvider() == null) {
            groups.firstNotNullOfOrNull { group ->
                runCatching { providers(group).firstOrNull() }.getOrNull()
            }?.let { setActiveProvider(it.id, it.group, it.name) }
        }
    }

    /**
     * Start — or join — the single first-launch installation, running on a scope that outlives
     * whatever screen kicked it off. Each HTTP call inside [ensureDefaultsInstalled] is capped,
     * but the aggregate (3 groups x N repos x M plugins) runs for minutes on a slow link, so no
     * UI may block on the whole of it. Await the returned [Deferred] with a timeout: abandoning
     * the await leaves the installation running instead of cancelling it.
     */
    fun installDefaultsAsync(progress: ((String, Int, Int) -> Unit)? = null): Deferred<Unit> =
        defaultsInstall ?: synchronized(this) {
            defaultsInstall ?: installScope
                .async { ensureDefaultsInstalled(progress = progress) }
                .also { defaultsInstall = it }
        }

    /** True once a source has been picked — used to decide whether first-launch setup is needed. */
    fun hasActiveProvider(): Boolean = getActiveProvider() != null

    /**
     * Providers for a group, with adult sources filtered out unless the user
     * opted in (Settings -> NSFW, default off).
     *
     * Filtered HERE because this is the single chokepoint every surface goes
     * through — the source picker, home, search and `searchAll` all call it. The
     * `nsfw` flag was already parsed and carried on [ExtProvider]; nothing
     * checked it, so the setting existed but changed nothing.
     */
    suspend fun providers(group: String): List<ExtProvider> = withContext(Dispatchers.IO) {
        val b = backendForGroup(group) ?: return@withContext emptyList()
        b.ensureLoaded()
        val all = ExtParser.providers(b.providersJson())
        if (nsfwAllowed()) all else all.filterNot { it.nsfw }
    }

    private fun nsfwAllowed(): Boolean =
        runCatching { PreferenceManager().isNsfwEnabled() }.getOrDefault(false)

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

    /**
     * "All sources", emitted INCREMENTALLY: one item per provider, the moment
     * that provider answers.
     *
     * The previous version awaited every provider before returning anything, so
     * a single dead mirror held the whole screen blank for the full timeout even
     * when eight sources had already replied in under a second. On a TV, where
     * there is no spinner worth watching and no way to peek at partial results,
     * that reads as "search is broken".
     *
     * Four bounds, all load-bearing — a CloudStream repo alone can install
     * dozens of providers:
     *   - [maxProviders] caps how many are queried at all;
     *   - [concurrency] caps how many run AT ONCE. The old fan-out launched all
     *     twelve together, and each one may dex-load an extension APK; on a
     *     low-end box that is a thundering herd that slows every leg down;
     *   - [perProviderTimeoutMs] caps each leg independently, so one stalled
     *     source costs its own slot and nothing else;
     *   - cancelling the flow stops scheduling further work.
     *
     * The active provider is queried FIRST so its hits arrive first — that is
     * the source the user already chose.
     *
     * Every leg reports a [SearchLegStatus] rather than being silently dropped.
     * "This source is down" and "no match here" look identical to a user
     * otherwise, and only one of them is worth retrying.
     */
    fun searchAllFlow(
        query: String,
        maxProviders: Int = MAX_GLOBAL_PROVIDERS,
        concurrency: Int = GLOBAL_SEARCH_CONCURRENCY,
        perProviderTimeoutMs: Long = GLOBAL_SEARCH_TIMEOUT_MS,
    ): Flow<SearchLeg> = channelFlow {
        val q = query.trim()
        if (q.isEmpty()) return@channelFlow

        val active = getActiveProvider()
        val candidates = listOf(ExtGroup.SERVER, ExtGroup.ANIYOMI, ExtGroup.CLOUDSTREAM)
            .flatMap { g -> runCatching { providers(g) }.getOrDefault(emptyList()) }
            .distinctBy { it.id }
            .sortedByDescending { it.id == active }
            .take(maxProviders)

        if (candidates.isEmpty()) return@channelFlow

        // A plain index shared by the workers: each takes the next provider when
        // it frees up, which keeps exactly `concurrency` searches in flight
        // rather than starting them all and hoping.
        val next = AtomicInteger(0)
        val workers = minOf(concurrency, candidates.size).coerceAtLeast(1)

        coroutineScope {
            repeat(workers) {
                launch(Dispatchers.IO) {
                    while (isActive) {
                        val i = next.getAndIncrement()
                        if (i >= candidates.size) return@launch
                        send(searchOne(candidates[i], q, perProviderTimeoutMs))
                    }
                }
            }
        }
    }

    /** One provider's leg. Never throws — the outcome is carried in the result. */
    private suspend fun searchOne(
        provider: ExtProvider,
        query: String,
        timeoutMs: Long,
    ): SearchLeg = try {
        val items = withTimeoutOrNull(timeoutMs) {
            search(provider.id, query)?.items.orEmpty()
        }
        when {
            items == null -> SearchLeg(provider, emptyList(), SearchLegStatus.TIMEOUT)
            items.isEmpty() -> SearchLeg(provider, emptyList(), SearchLegStatus.EMPTY)
            else -> SearchLeg(provider, items, SearchLegStatus.OK)
        }
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Log.w("ExtensionEngine", "search failed on ${provider.id}: ${t.message}")
        SearchLeg(provider, emptyList(), SearchLegStatus.ERROR)
    }

    /**
     * Blocking "all sources" search, kept for callers that genuinely need the
     * whole set at once. Built on [searchAllFlow] so there is one fan-out
     * implementation rather than two that drift apart.
     */
    suspend fun searchAll(
        query: String,
        maxProviders: Int = MAX_GLOBAL_PROVIDERS,
        perProviderTimeoutMs: Long = GLOBAL_SEARCH_TIMEOUT_MS,
    ): List<ExtCard> {
        val seen = HashSet<String>()
        return searchAllFlow(query, maxProviders, perProviderTimeoutMs = perProviderTimeoutMs)
            .toList()
            .flatMap { it.items }
            .filter { seen.add("${it.provider}|${it.contentUrl}") }
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

        /** Global-search bounds — see [searchAllFlow]. */
        private const val MAX_GLOBAL_PROVIDERS = 12

        /**
         * How many provider searches run at once.
         *
         * Four, not twelve: each leg may download and dex-load an extension APK
         * before it can issue a single request, and a TV box has a fraction of a
         * phone's CPU. Running them all together made every leg slower and made
         * the first result arrive later, which is the opposite of the point.
         */
        private const val GLOBAL_SEARCH_CONCURRENCY = 4

        /**
         * Per-provider budget.
         *
         * Raised from 12s because the first search against a freshly-installed
         * source has to fetch and dex-load its extension before it can do
         * anything — several megabytes over whatever connection the box has.
         * Under the old ceiling every extension timed out on first use, which is
         * exactly when the user had just added sources and was watching.
         * Later searches hit the cached extension and return in well under a
         * second, so this is only ever paid once per source.
         */
        private const val GLOBAL_SEARCH_TIMEOUT_MS = 40_000L

        val shared: ExtensionEngine by lazy { ExtensionEngine() }
    }
}
