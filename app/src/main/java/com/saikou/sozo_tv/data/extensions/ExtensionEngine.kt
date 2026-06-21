package com.saikou.sozo_tv.data.extensions

import android.content.Context
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.engine.aniyomi.AniyomiHost
import com.saikou.sozo_tv.engine.aniyomi.AniyomiRepoManager
import com.saikou.sozo_tv.engine.cloudstream.PluginHost
import com.saikou.sozo_tv.engine.cloudstream.RepoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Single entry point the app's data layer uses to talk to the Aniyomi/CloudStream
 * extension engines. Hides the two hosts behind one suspend API and routes calls by
 * provider-id prefix (`an:` → Aniyomi, `cs:` → CloudStream). Also owns the "active
 * provider" selection that Home / Search / Categories read.
 */
class ExtensionEngine(private val appContext: Context = MyApp.context) {

    private val aniyomiHost by lazy { AniyomiHost(appContext) }
    private val aniyomiRepos by lazy { AniyomiRepoManager(appContext, aniyomiHost) }
    private val csHost by lazy { PluginHost(appContext) }
    private val csRepos by lazy { RepoManager(appContext, csHost) }

    private val prefs by lazy { appContext.getSharedPreferences("ext_engine", Context.MODE_PRIVATE) }


    fun getActiveGroup(): String = prefs.getString(KEY_GROUP, ExtGroup.ANIYOMI) ?: ExtGroup.ANIYOMI

    fun getActiveProvider(): String? = prefs.getString(KEY_PROVIDER, null)?.ifEmpty { null }

    fun setActiveProvider(id: String, group: String) {
        prefs.edit().putString(KEY_PROVIDER, id).putString(KEY_GROUP, group).apply()
    }

    /** Resolve the provider id to use for a given content (falls back to active). */
    private fun providerFor(id: String?): String? = id?.ifEmpty { null } ?: getActiveProvider()

    private fun isAniyomi(provider: String) = provider.startsWith("an:")
    private fun bareId(provider: String) = provider.removePrefix("an:").removePrefix("cs:")

    /**
     * Lazily restore the provider's group into its host before any read (home/search/load/…),
     * so a cold start with a previously-selected source works without first opening Sources.
     * [ensureLoaded] is cached (no-op after the first call), so this is cheap to call everywhere.
     */
    private fun ensureHostLoaded(provider: String) {
        if (isAniyomi(provider)) aniyomiRepos.ensureLoaded() else csRepos.ensureLoaded()
    }


    suspend fun ensureLoaded(group: String) = withContext(Dispatchers.IO) {
        if (group == ExtGroup.ANIYOMI) aniyomiRepos.ensureLoaded() else csRepos.ensureLoaded()
    }

    suspend fun providers(group: String): List<ExtProvider> = withContext(Dispatchers.IO) {
        if (group == ExtGroup.ANIYOMI) {
            aniyomiRepos.ensureLoaded(); ExtParser.providers(aniyomiHost.providersJson())
        } else {
            csRepos.ensureLoaded(); ExtParser.providers(csHost.providersJson())
        }
    }

    suspend fun listRepos(group: String): List<ExtRepo> = withContext(Dispatchers.IO) {
        if (group == ExtGroup.ANIYOMI) ExtParser.repos(aniyomiRepos.listReposJson())
        else ExtParser.repos(csRepos.listReposJson())
    }

    /** Add a repo by resolved URL. Returns number of sources/plugins installed. */
    suspend fun addRepo(group: String, url: String, progress: ((Int, Int) -> Unit)? = null): Int =
        withContext(Dispatchers.IO) {
            val result: JSONObject = if (group == ExtGroup.ANIYOMI) aniyomiRepos.addRepo(url, progress)
            else csRepos.addRepo(url, progress)
            result.optInt("sourceCount", result.optInt("pluginCount", 0))
        }

    suspend fun removeRepo(group: String, url: String) = withContext(Dispatchers.IO) {
        if (group == ExtGroup.ANIYOMI) aniyomiRepos.removeRepo(url) else csRepos.removeRepo(url)
        Unit
    }

    suspend fun checkUpdates(): Int = withContext(Dispatchers.IO) {
        csRepos.checkUpdates().optInt("count", 0)
    }


    suspend fun home(provider: String? = null, page: Int = 1): ExtHome? = withContext(Dispatchers.IO) {
        val p = providerFor(provider) ?: return@withContext null
        ensureHostLoaded(p)
        val json = if (isAniyomi(p)) aniyomiHost.getMainPageJson(bareId(p), page)
        else csHost.getMainPageJson(p, page)
        ExtParser.home(json)
    }

    suspend fun section(provider: String?, slug: String, page: Int = 1): ExtPage? =
        withContext(Dispatchers.IO) {
            val p = providerFor(provider) ?: return@withContext null
            ensureHostLoaded(p)
            val json = if (isAniyomi(p)) aniyomiHost.getSectionJson(bareId(p), slug, page)
            else csHost.getSectionJson(p, slug, page)
            ExtParser.page(json)
        }

    suspend fun genres(provider: String? = null): List<ExtGenre> = withContext(Dispatchers.IO) {
        val p = providerFor(provider) ?: return@withContext emptyList()
        ensureHostLoaded(p)
        val json = if (isAniyomi(p)) aniyomiHost.getGenresJson(bareId(p)) else csHost.getGenresJson(p)
        ExtParser.genres(json)
    }

    suspend fun search(provider: String?, query: String): ExtPage? = withContext(Dispatchers.IO) {
        val p = providerFor(provider) ?: return@withContext null
        ensureHostLoaded(p)
        val json = if (isAniyomi(p)) aniyomiHost.searchJson(bareId(p), query)
        else csHost.searchJson(p, query)
        ExtParser.page(json)
    }

    suspend fun load(provider: String, url: String): ExtDetail? = withContext(Dispatchers.IO) {
        ensureHostLoaded(provider)
        val json = if (isAniyomi(provider)) aniyomiHost.loadJson(bareId(provider), url)
        else csHost.loadJson(provider, url)
        ExtParser.detail(json)
    }

    suspend fun loadLinks(provider: String, mediaRef: String): ExtMedia = withContext(Dispatchers.IO) {
        ensureHostLoaded(provider)
        val json = if (isAniyomi(provider)) aniyomiHost.loadLinksJson(bareId(provider), mediaRef)
        else csHost.loadLinksJson(provider, mediaRef)
        ExtParser.media(json)
    }

    companion object {
        private const val KEY_GROUP = "active_group"
        private const val KEY_PROVIDER = "active_provider"

        /**
         * Process-wide instance so the (non-Koin) [com.saikou.sozo_tv.parser.sources.ExtensionParser]
         * and the Koin graph share one engine — and therefore one set of host caches
         * (loaded plugins / provider registry).
         */
        val shared: ExtensionEngine by lazy { ExtensionEngine() }
    }
}
