package com.lagradost.cloudstream3.plugins

import android.content.Context
import java.io.File

/**
 * What CloudStream records about an installed plugin.
 *
 * The five fields are not optional decoration: Ultima calls `copy()` on one of these, and
 * `copy$default` carries the whole primary constructor in its JVM descriptor, so a shorter
 * data class is a `NoSuchMethodError` even for a plugin that only reads [internalName].
 * Field order matches CloudStream's for the same reason.
 */
data class PluginData(
    val internalName: String,
    val url: String?,
    val isOnline: Boolean,
    val filePath: String,
    val version: Int,
)

/**
 * A stand-in for CloudStream's plugin registry. See
 * [com.lagradost.cloudstream3.utils.Event]'s file for why these classes exist.
 *
 * This app keeps its own registry in [com.saikou.sozo_tv.engine.cloudstream.PluginHost], so this
 * is a facade over that rather than a second source of truth — two registries disagreeing about
 * what is installed is worse than one a plugin cannot see into.
 */
object PluginManager {

    /**
     * Filled by [com.saikou.sozo_tv.engine.cloudstream.PluginHost] as plugins load.
     *
     * A plugin walks [getPluginsOnline] to find its own `.cs3` on disk and read a resource out of
     * it, which is the one case that matters and the one that always works: by the time a
     * plugin's own code runs, its entry is already here.
     */
    private val installed = linkedMapOf<String, PluginData>()

    fun getPluginsOnline(): Array<PluginData> = installed.values.toTypedArray()

    /**
     * Empty, unlike [getPluginsOnline].
     *
     * Upstream this maps a file path to the live `Plugin` instance. Handing those out lets one
     * plugin call another's `load()` behind the host's back, on whatever thread it likes, while
     * [com.saikou.sozo_tv.engine.cloudstream.PluginHost] serialises loading precisely so its
     * provider diff stays correct.
     */
    fun getPlugins(): Map<String, Any> = emptyMap()

    /**
     * The `.cs3` this app has on disk for [internalName], or where one would live.
     *
     * [repositoryUrl] is ignored: files are cached per plugin, not per repo, so the recorded
     * path is the answer whenever there is one.
     */
    fun getPluginPath(context: Context, internalName: String, repositoryUrl: String): File =
        installed[internalName]?.filePath?.let { File(it) }
            ?: File(File(context.filesDir, "cs3"), "$internalName.cs3")

    /**
     * Ignored, and returns false so a caller sees the plugin did not load.
     *
     * Installing an extension is the viewer's decision, made in the Sources screen. A plugin
     * pulling another one in would add providers to the list that no repo the viewer added
     * accounts for, and that nothing removes when a repo is removed.
     */
    suspend fun loadSinglePlugin(context: Context, apiName: String): Boolean = false

    /**
     * Ignored.
     *
     * Unloading is the host's decision, not a plugin's — providers are dropped through
     * [com.saikou.sozo_tv.engine.cloudstream.PluginHost.removeProviders] when a repo is removed.
     * Honouring this would let one plugin unload another out from under the source the viewer is
     * currently watching.
     */
    fun unloadPlugin(internalName: String) {
        // Deliberately empty; see above.
    }

    /** Called by the host when a `.cs3` finishes loading. */
    fun record(internalName: String, filePath: String) {
        // url and version stay unanswered: the host caches a `.cs3` by name and version and
        // keeps no download url per plugin. Ultima is the only reader of `url`, and it uses it
        // to match installed plugins against the repositories [RepositoryManager] reports —
        // which are none, so a real url would change nothing it shows.
        installed[internalName] = PluginData(internalName, null, true, filePath, 0)
    }

    /** Called by the host when a plugin's providers are dropped. */
    fun forget(internalName: String) {
        installed.remove(internalName)
    }
}
