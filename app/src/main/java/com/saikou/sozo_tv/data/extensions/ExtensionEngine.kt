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
import kotlinx.coroutines.withContext

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

    fun getActiveGroup(): String = prefs.getString(KEY_GROUP, ExtGroup.ANIYOMI) ?: ExtGroup.ANIYOMI

    fun getActiveProvider(): String? = prefs.getString(KEY_PROVIDER, null)?.ifEmpty { null }

    fun setActiveProvider(id: String, group: String) {
        prefs.edit().putString(KEY_PROVIDER, id).putString(KEY_GROUP, group).apply()
    }

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

    suspend fun home(provider: String? = null, page: Int = 1): ExtHome? = withContext(Dispatchers.IO) {
        val p = providerFor(provider) ?: return@withContext null
        val b = backendForProvider(p) ?: return@withContext null
        b.ensureLoaded()
        ExtParser.home(b.getMainPageJson(p, page))
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

        val shared: ExtensionEngine by lazy { ExtensionEngine() }
    }
}
