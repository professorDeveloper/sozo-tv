package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData

/**
 * Stand-ins for CloudStream's repository layer. See [com.lagradost.cloudstream3.utils.Event]'s
 * file for why these classes exist.
 *
 * These are reached by META plugins — ones that manage other plugins rather than serve content.
 * Ultima is the example in the phisher repo: it lists the repos you have added and the plugins
 * inside them to offer its own combined view.
 */

/** One plugin as listed in a repository's `plugins.json`. */
data class SitePlugin(
    val internalName: String,
    val url: String,
)

/** A plugin together with the repository it came from. */
data class PluginWrapper(
    val plugin: SitePlugin,
    val repositoryData: RepositoryData,
)

/**
 * Reports an empty installation.
 *
 * Repositories live in [com.saikou.sozo_tv.engine.cloudstream.RepoManager]'s own preferences and
 * the viewer manages them from the Sources screen. Handing the list to a plugin invites it to
 * act on entries whose state it cannot see — the Sources screen groups providers by repo and by
 * engine, neither of which CloudStream has a concept of — and would leave the app's list and the
 * actual state disagreeing, with the app's list being the one the viewer is looking at.
 */
object RepositoryManager {

    fun getRepositories(): Array<RepositoryData> = emptyArray()

    suspend fun getRepoPlugins(repository: RepositoryData): List<PluginWrapper>? = emptyList()
}
