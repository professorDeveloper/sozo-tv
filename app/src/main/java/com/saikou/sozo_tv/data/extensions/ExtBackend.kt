package com.saikou.sozo_tv.data.extensions

import com.saikou.sozo_tv.engine.aniyomi.AniyomiHost
import com.saikou.sozo_tv.engine.aniyomi.AniyomiRepoManager
import com.saikou.sozo_tv.engine.cloudstream.PluginHost
import com.saikou.sozo_tv.engine.cloudstream.RepoManager
import com.saikou.sozo_tv.engine.server.ServerHost
import org.json.JSONObject

interface ExtBackend {
    val group: String

    fun ensureLoaded()
    fun providersJson(): String
    fun listReposJson(): String
    fun addRepo(url: String, progress: ((Int, Int) -> Unit)?): JSONObject
    fun removeRepo(url: String): String
    fun checkUpdates(progress: ((Int, Int) -> Unit)?): JSONObject

    suspend fun getMainPageJson(provider: String, page: Int): String
    suspend fun getSectionJson(provider: String, data: String, page: Int): String
    suspend fun searchJson(provider: String, query: String): String
    suspend fun getGenresJson(provider: String): String
    suspend fun loadJson(provider: String, url: String): String
    suspend fun loadLinksJson(provider: String, data: String): String
}

class AniyomiBackend(
    private val host: AniyomiHost,
    private val repos: AniyomiRepoManager,
) : ExtBackend {
    override val group = ExtGroup.ANIYOMI

    private fun bare(provider: String) = provider.removePrefix("an:")

    override fun ensureLoaded() = repos.ensureLoaded()
    override fun providersJson() = host.providersJson()
    override fun listReposJson() = repos.listReposJson()
    override fun addRepo(url: String, progress: ((Int, Int) -> Unit)?) = repos.addRepo(url, progress)
    override fun removeRepo(url: String) = repos.removeRepo(url)
    override fun checkUpdates(progress: ((Int, Int) -> Unit)?) = JSONObject()

    override suspend fun getMainPageJson(provider: String, page: Int) =
        host.getMainPageJson(bare(provider), page)

    override suspend fun getSectionJson(provider: String, data: String, page: Int) =
        host.getSectionJson(bare(provider), data, page)

    override suspend fun searchJson(provider: String, query: String) =
        host.searchJson(bare(provider), query)

    override suspend fun getGenresJson(provider: String) = host.getGenresJson(bare(provider))
    override suspend fun loadJson(provider: String, url: String) = host.loadJson(bare(provider), url)
    override suspend fun loadLinksJson(provider: String, data: String) =
        host.loadLinksJson(bare(provider), data)
}

class CloudStreamBackend(
    private val host: PluginHost,
    private val repos: RepoManager,
) : ExtBackend {
    override val group = ExtGroup.CLOUDSTREAM

    override fun ensureLoaded() = repos.ensureLoaded()
    override fun providersJson() = host.providersJson()
    override fun listReposJson() = repos.listReposJson()
    override fun addRepo(url: String, progress: ((Int, Int) -> Unit)?) = repos.addRepo(url, progress)
    override fun removeRepo(url: String) = repos.removeRepo(url)
    override fun checkUpdates(progress: ((Int, Int) -> Unit)?) = repos.checkUpdates(progress)

    override suspend fun getMainPageJson(provider: String, page: Int) =
        host.getMainPageJson(provider, page)

    override suspend fun getSectionJson(provider: String, data: String, page: Int) =
        host.getSectionJson(provider, data, page)

    override suspend fun searchJson(provider: String, query: String) =
        host.searchJson(provider, query)

    override suspend fun getGenresJson(provider: String) = host.getGenresJson(provider)
    override suspend fun loadJson(provider: String, url: String) = host.loadJson(provider, url)
    override suspend fun loadLinksJson(provider: String, data: String) =
        host.loadLinksJson(provider, data)
}

class ServerBackend(private val host: ServerHost) : ExtBackend {
    override val group = ExtGroup.SERVER

    override fun ensureLoaded() = host.ensureLoaded()
    override fun providersJson() = host.providersJson()
    override fun listReposJson() = "[]"
    override fun addRepo(url: String, progress: ((Int, Int) -> Unit)?) = JSONObject()
    override fun removeRepo(url: String) = ""
    override fun checkUpdates(progress: ((Int, Int) -> Unit)?) = JSONObject()

    override suspend fun getMainPageJson(provider: String, page: Int) =
        host.getMainPageJson(provider, page)

    override suspend fun getSectionJson(provider: String, data: String, page: Int) =
        host.getSectionJson(provider, data, page)

    override suspend fun searchJson(provider: String, query: String) =
        host.searchJson(provider, query)

    override suspend fun getGenresJson(provider: String) = host.getGenresJson(provider)
    override suspend fun loadJson(provider: String, url: String) = host.loadJson(provider, url)
    override suspend fun loadLinksJson(provider: String, data: String) =
        host.loadLinksJson(provider, data)
}
