package com.saikou.sozo_tv.engine.cloudstream

import com.saikou.sozo_tv.utils.SOZO_USER_AGENT
import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads CloudStream repos and feeds the `.cs3` files to [PluginHost].
 *
 * Accepts:
 *   - a direct `repo.json` URL  ( {name, pluginLists:[plugins.json url, ...]} )
 *   - a direct `plugins.json` URL ( [ {name, internalName, url(.cs3), version, ...} ] )
 *   - a CloudStream shortcode (resolved via l.cloudstream.app → repo url)
 *
 * `.cs3` files are cached under filesDir/cs3/<internalName>@<version>.cs3 so a
 * version bump re-downloads. This is the Android-only feature backing the
 * `soplay/cloudstream` MethodChannel. Runtime testing happens on device.
 */
class RepoManager(private val context: Context, private val host: PluginHost) {

    /** One `plugins.json` entry, reduced to what installing it needs. */
    internal data class PluginRef(
        val url: String, val internalName: String, val version: Int, val iconUrl: String?,
    )

    /** A `repo.json`, reduced to a display name and the plugin lists it points at. */
    internal data class RepoInfo(val name: String?, val pluginListUrls: List<String>)

    companion object {
        private const val TAG = "CloudStreamRepo"
        private val UA get() = SOZO_USER_AGENT

        /** 30x hops chased before a fetch is abandoned. */
        private const val MAX_REDIRECTS = 5

        /** `status` a repo author sets on a plugin they know is broken; CloudStream hides those. */
        private const val STATUS_DOWN = 0

        /** A `.cs3` is a zip; these are the four bytes of its local file header. */
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

        /**
         * `repo.json` `{name, pluginLists:[...]}`, or a body that is already a plugins.json array.
         *
         * Each `pluginLists` element is read with `optString`. The whole array used to be read
         * with `getString`, so one element that was not a string — a null left by a generator,
         * an object — threw out of the enclosing parse and the repo installed nothing at all
         * instead of losing that one list.
         */
        internal fun parseRepoIndex(body: String, repoUrl: String): RepoInfo {
            if (body.trimStart().startsWith("[")) return RepoInfo(null, listOf(repoUrl))
            return try {
                val o = JSONObject(body)
                val arr = o.optJSONArray("pluginLists")
                val urls = (0 until (arr?.length() ?: 0))
                    .mapNotNull { arr?.optString(it)?.trim()?.ifEmpty { null } }
                RepoInfo(o.optString("name").ifEmpty { null }, urls)
            } catch (t: Throwable) {
                Log.e(TAG, "parse repo.json failed: ${t.message}")
                RepoInfo(null, emptyList())
            }
        }

        /**
         * Plugin descriptors from one `plugins.json` body. An entry that cannot be read costs
         * that entry alone; only a body that is not a JSON array costs the whole list.
         */
        internal fun parsePluginList(body: String): List<PluginRef> {
            val plugins = try { JSONArray(body) } catch (_: Throwable) { return emptyList() }
            val out = ArrayList<PluginRef>(plugins.length())
            for (i in 0 until plugins.length()) {
                val p = plugins.optJSONObject(i) ?: continue
                val url = p.optString("url").trim()
                if (url.isEmpty()) continue
                // Downloading a plugin the repo marks as down spends bandwidth and dex
                // verification to put a source in the list that answers nothing. An entry
                // without the field is treated as up, so repos that omit it are unaffected.
                if (p.optInt("status", 1) == STATUS_DOWN) continue
                val internalName = p.optString("internalName").ifEmpty { p.optString("name") }
                    .ifEmpty { "plugin$i" }
                out.add(
                    PluginRef(
                        url = url,
                        internalName = internalName,
                        version = p.optInt("version", 0),
                        iconUrl = p.optString("iconUrl").ifEmpty { null },
                    )
                )
            }
            return out
        }

        /**
         * One entry per plugin, highest version winning, first-seen order kept.
         *
         * A repo may list the same plugin in more than one pluginList (a per-language list
         * plus an "all" list is common). Each duplicate was downloaded and loaded again, and
         * appended another copy of that plugin's providers to the repo's saved metadata.
         */
        internal fun dedupePlugins(refs: List<PluginRef>): List<PluginRef> {
            val byName = LinkedHashMap<String, PluginRef>()
            for (r in refs) {
                val existing = byName[r.internalName]
                if (existing == null || r.version > existing.version) byName[r.internalName] = r
            }
            return byName.values.toList()
        }
    }

    private val cs3Dir: File = File(context.filesDir, "cs3").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("cloudstream", Context.MODE_PRIVATE)
    @Volatile private var ensured = false

    private fun savedRepos(): MutableList<String> {
        val raw = prefs.getString("repos", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw); MutableList(arr.length()) { arr.getString(it) }
        } catch (_: Throwable) { mutableListOf() }
    }

    private fun persist(repos: List<String>) {
        prefs.edit().putString("repos", JSONArray(repos).toString()).apply()
    }

    // Persisted provider metadata per repo: { repoInput: [{provider,icon,internalName,cs3Path}] }
    private fun loadMeta(): JSONObject =
        try { JSONObject(prefs.getString("meta", "{}") ?: "{}") } catch (_: Throwable) { JSONObject() }

    private fun saveMeta(o: JSONObject) {
        prefs.edit().putString("meta", o.toString()).apply()
    }

    /**
     * Make saved repos' providers available — WITHOUT loading every .cs3.
     * We only register cached metadata (name/icon/path); each plugin is loaded
     * lazily on first use. Falls back to a full load for repos saved before
     * metadata existed.
     */
    @Synchronized
    fun ensureLoaded() {
        if (ensured) return
        ensured = true
        val meta = loadMeta()
        val names = loadNames()
        for (repo in savedRepos()) {
            val repoName = names.optString(repo).ifEmpty { fallbackName(repo) }
            val entries = meta.optJSONArray(repo)
            // No metadata at all (a repo saved before metadata existed), or metadata that
            // registers nothing: load the repo in full so its providers get another chance.
            // An empty array used to take the branch below and register zero providers, which
            // made one failed install permanent — every plugin in a repo failing to load once
            // left that repo showing no sources on every later launch, with no way to retry
            // short of clearing app data.
            if (entries == null || entries.length() == 0) {
                try { addRepoInternal(repo) } catch (t: Throwable) { Log.e(TAG, "ensureLoaded $repo: ${t.message}") }
                continue
            }
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                host.registerMeta(
                    e.optString("provider"),
                    e.optString("icon").ifEmpty { null },
                    e.optString("internalName"),
                    e.optString("cs3Path"),
                    repoName,
                )
            }
        }
    }

    fun listReposJson(): String {
        val names = loadNames()
        val arr = JSONArray()
        for (r in savedRepos()) {
            arr.put(JSONObject().apply {
                put("url", r)
                put("name", names.optString(r).ifEmpty { fallbackName(r) })
            })
        }
        return arr.toString()
    }

    fun removeRepo(input: String): String {
        val key = input.trim()
        val meta = loadMeta()
        val entries = meta.optJSONArray(key)
        if (entries != null) {
            val providers = (0 until entries.length())
                .mapNotNull { entries.optJSONObject(it)?.optString("provider") }
                .filter { it.isNotEmpty() }
            val internalNames = (0 until entries.length())
                .mapNotNull { entries.optJSONObject(it)?.optString("internalName") }
                .filter { it.isNotEmpty() }
                .toSet()
            host.removeProviders(providers)
            meta.remove(key); saveMeta(meta)
            deleteUnreferencedCs3(internalNames, meta)
        }
        val names = loadNames(); names.remove(key); saveNames(names)
        val repos = savedRepos()
        repos.remove(key)
        persist(repos)
        return JSONObject().apply { put("repos", JSONArray(repos)) }.toString()
    }

    /**
     * Delete cached archives for [internalNames] that no repo left in [meta] still lists.
     *
     * The `.cs3` files stayed on disk after a repo was removed and nothing ever collected
     * them, so the space a repo occupied — tens of megabytes on a box that has little —
     * could not be reclaimed at all.
     */
    private fun deleteUnreferencedCs3(internalNames: Set<String>, meta: JSONObject) {
        if (internalNames.isEmpty()) return
        val stillReferenced = HashSet<String>()
        meta.keys().forEach { repo ->
            val entries = meta.optJSONArray(repo) ?: return@forEach
            for (i in 0 until entries.length()) {
                entries.optJSONObject(i)?.optString("internalName")
                    ?.takeIf { it.isNotEmpty() }?.let(stillReferenced::add)
            }
        }
        val orphans = internalNames - stillReferenced
        if (orphans.isEmpty()) return
        cs3Dir.listFiles()?.forEach { f ->
            if (orphans.any { f.name.startsWith("$it@") }) f.delete()
        }
    }

    /**
     * A connection already positioned on a 2xx response, or null.
     *
     * `instanceFollowRedirects` stays on, so the platform handles the ordinary hops; a 30x
     * only reaches this loop when it declined to follow one — java.net refuses any redirect
     * that changes scheme. That refusal arrived here as `GET <url> -> 301` and the repo
     * installed nothing, and repo urls behind a shortener or a `github.com/.../raw/` path
     * are served over exactly such a hop.
     */
    private fun openStream(url: String, readTimeoutMs: Int): HttpURLConnection? {
        var target = url
        repeat(MAX_REDIRECTS + 1) {
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; instanceFollowRedirects = true
                connectTimeout = 20000; readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", UA)
            }
            val code = conn.responseCode
            if (code in 200..299) return conn
            val location = if (code in 300..399) conn.getHeaderField("Location") else null
            conn.disconnect()
            if (location.isNullOrEmpty()) { Log.e(TAG, "GET $target -> $code"); return null }
            target = URL(URL(target), location).toString()
        }
        Log.e(TAG, "GET $url: more than $MAX_REDIRECTS redirects")
        return null
    }

    private fun httpGet(url: String): String? = try {
        openStream(url, readTimeoutMs = 30000)?.inputStream?.bufferedReader()?.use { it.readText() }
    } catch (t: Throwable) { Log.e(TAG, "GET $url failed: ${t.message}"); null }

    /** repo.json {name, pluginLists:[...]} OR a direct plugins.json array. */
    private fun fetchRepo(repoUrl: String): RepoInfo {
        val body = httpGet(repoUrl) ?: return RepoInfo(null, emptyList())
        return parseRepoIndex(body, repoUrl)
    }

    /** Every plugin a repo offers, across all of its plugin lists, deduplicated. */
    private fun fetchPlugins(info: RepoInfo): List<PluginRef> = dedupePlugins(
        info.pluginListUrls.flatMap { parsePluginList(httpGet(it) ?: return@flatMap emptyList()) }
    )

    // Persisted display names: { repoInput: "Repo Name" }
    private fun loadNames(): JSONObject =
        try { JSONObject(prefs.getString("names", "{}") ?: "{}") } catch (_: Throwable) { JSONObject() }
    private fun saveNames(o: JSONObject) { prefs.edit().putString("names", o.toString()).apply() }

    /**
     * Fetch one plugin into the cache, or hand back the cached copy.
     *
     * The bytes land in a `.part` file and are only moved into place once they are all there
     * and start like a zip. Writing straight to the final name meant a dropped connection, a
     * killed process or an HTML error page served with a 200 (a rate-limit notice, a captive
     * portal) left a file that `exists() && length > 0` accepted on every later launch: that
     * plugin never loaded again, and clearing app data was the only way back. The same check
     * runs over an already-cached file so a copy left by an earlier build is replaced rather
     * than trusted.
     */
    private fun downloadCs3(internalName: String, version: Int, url: String): File? {
        val file = File(cs3Dir, "$internalName@$version.cs3")
        if (file.exists() && file.length() > 0) {
            if (looksLikeZip(file)) return file
            Log.w(TAG, "cached ${file.name} is not an archive; re-downloading")
            file.delete()
        }
        val part = File(cs3Dir, "${file.name}.part")
        return try {
            val conn = openStream(url, readTimeoutMs = 60000) ?: return null
            val declared = conn.contentLengthLong
            conn.inputStream.use { input -> FileOutputStream(part).use { input.copyTo(it) } }
            val written = part.length()
            when {
                declared > 0 && written != declared ->
                    discardPart(part, "cs3 $url truncated: $written of $declared bytes")
                !looksLikeZip(part) ->
                    discardPart(part, "cs3 $url is not an archive ($written bytes)")
                !part.renameTo(file) ->
                    discardPart(part, "cs3 $url: could not move into ${file.name}")
                else -> {
                    // drop stale versions of the same plugin
                    cs3Dir.listFiles()?.forEach { f ->
                        if (f.name.startsWith("$internalName@") && f.name != file.name) f.delete()
                    }
                    file
                }
            }
        } catch (t: Throwable) {
            part.delete()
            Log.e(TAG, "download cs3 failed: ${t.message}")
            null
        }
    }

    private fun discardPart(part: File, message: String): File? {
        part.delete()
        Log.e(TAG, message)
        return null
    }

    private fun looksLikeZip(f: File): Boolean = try {
        FileInputStream(f).use { input ->
            val head = ByteArray(ZIP_MAGIC.size)
            input.read(head) == head.size && head.contentEquals(ZIP_MAGIC)
        }
    } catch (_: Throwable) { false }

    /**
     * Add a repo (url or shortcode): download all plugins, load them, and return
     * the registered provider names. Synchronous network — call off the main thread.
     */
    fun addRepo(input: String, progress: ((Int, Int) -> Unit)? = null): JSONObject {
        val result = addRepoInternal(input, progress)
        if (result.optInt("pluginCount") > 0) {
            val repos = savedRepos()
            val v = input.trim()
            if (!repos.contains(v)) { repos.add(v); persist(repos) }
        }
        return result
    }

    private fun fallbackName(url: String): String {
        val gh = Regex("github(?:usercontent)?\\.com/([^/]+)/([^/]+)").find(url)
        if (gh != null) return "${gh.groupValues[1]}/${gh.groupValues[2]}"
        return try { java.net.URL(url).host ?: url } catch (_: Throwable) { url }
    }

    private fun addRepoInternal(input: String, progress: ((Int, Int) -> Unit)? = null): JSONObject {
        val repoUrl = input.trim()
        val info = fetchRepo(repoUrl)
        val providers = JSONArray()
        val metaEntries = JSONArray()
        var pluginCount = 0

        // Gather every plugin descriptor first so the total is known up front and
        // we can report "downloaded N / M" progress to the install UI.
        val all = fetchPlugins(info)

        val total = all.size
        val repoName = info.name ?: fallbackName(repoUrl)
        progress?.invoke(0, total)
        for ((index, ref) in all.withIndex()) {
            val file = downloadCs3(ref.internalName, ref.version, ref.url)
            if (file != null) {
                pluginCount++
                // Load now to discover provider names (one-time on add); persist
                // metadata so future launches can lazy-load without this cost.
                host.loadCs3(file, ref.internalName, ref.iconUrl, repoName).forEach { name ->
                    providers.put(name)
                    metaEntries.put(JSONObject().apply {
                        put("provider", name)
                        if (ref.iconUrl != null) put("icon", ref.iconUrl)
                        put("internalName", ref.internalName)
                        put("cs3Path", file.absolutePath)
                    })
                }
            }
            progress?.invoke(index + 1, total)
        }
        // Persist this repo's provider metadata for lazy loading on next launch. An empty list
        // is REMOVED rather than stored: a stored empty array is indistinguishable from "this
        // repo really has no providers", and ensureLoaded() read it that way forever. Leaving
        // the key absent is what lets the next launch retry the load.
        val meta = loadMeta()
        if (metaEntries.length() > 0) meta.put(repoUrl, metaEntries) else meta.remove(repoUrl)
        saveMeta(meta)
        // Persist a friendly display name (from repo.json, else derived from url).
        if (pluginCount > 0) {
            val names = loadNames(); names.put(repoUrl, info.name ?: fallbackName(repoUrl)); saveNames(names)
        }
        Log.i(TAG, "addRepo($repoUrl): $pluginCount plugins, providers=$providers")
        return JSONObject().apply {
            put("repo", repoUrl); put("pluginCount", pluginCount)
            // Distinct from pluginCount: a plugin that downloads but registers no MainAPI adds
            // nothing selectable, and the caller needs to be able to say so.
            put("providerCount", providers.length())
            put("providers", providers)
        }
    }

    /**
     * Re-fetch every saved repo, re-download any plugin whose repo version is newer than the
     * installed one (parsed from the cached `@<version>.cs3` name), and install any plugin the
     * repo has published since. Updates metadata + reloads the plugin. Returns the affected
     * provider names.
     *
     * This is also the user's only way to recover a repo whose plugins all failed to install:
     * it holds no metadata, so every plugin in it reads as new here.
     */
    fun checkUpdates(progress: ((Int, Int) -> Unit)? = null): JSONObject {
        val meta = loadMeta()
        val names = loadNames()
        val updated = JSONArray()
        val repos = savedRepos()
        progress?.invoke(0, repos.size)
        for ((rIndex, repo) in repos.withIndex()) {
            val info = fetchRepo(repo)
            val latest = fetchPlugins(info).associateByTo(LinkedHashMap()) { it.internalName }
            val repoName = names.optString(repo).ifEmpty { info.name ?: fallbackName(repo) }
            val entries = meta.optJSONArray(repo) ?: JSONArray()
            val downloaded = HashSet<String>()
            val installed = HashSet<String>()
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                val internalName = e.optString("internalName")
                installed.add(internalName)
                val ref = latest[internalName] ?: continue
                val installedVer = e.optString("cs3Path")
                    .substringAfterLast('@', "").substringBefore(".cs3").toIntOrNull() ?: 0
                if (ref.version <= installedVer) continue
                val file = if (downloaded.add(internalName)) {
                    downloadCs3(ref.internalName, ref.version, ref.url)
                } else {
                    File(cs3Dir, "${ref.internalName}@${ref.version}.cs3").takeIf { it.exists() }
                }
                if (file != null) {
                    e.put("cs3Path", file.absolutePath)
                    host.loadCs3(file, ref.internalName, ref.iconUrl, repoName)
                    updated.put(e.optString("provider"))
                }
            }
            // Plugins the repo has published SINCE it was installed. This pass walked only the
            // entries already on disk, so a provider added to a repo stayed invisible until the
            // repo was removed and added again — and nothing in the UI removes a repo.
            for (ref in latest.values) {
                if (ref.internalName in installed) continue
                val file = downloadCs3(ref.internalName, ref.version, ref.url) ?: continue
                host.loadCs3(file, ref.internalName, ref.iconUrl, repoName).forEach { name ->
                    entries.put(JSONObject().apply {
                        put("provider", name)
                        if (ref.iconUrl != null) put("icon", ref.iconUrl)
                        put("internalName", ref.internalName)
                        put("cs3Path", file.absolutePath)
                    })
                    updated.put(name)
                }
            }
            // Same reasoning as addRepoInternal: an empty array persisted here would be read
            // as "this repo has no providers" on the next launch.
            if (entries.length() > 0) meta.put(repo, entries) else meta.remove(repo)
            if (names.optString(repo).isEmpty() && entries.length() > 0) {
                names.put(repo, repoName)
            }
            progress?.invoke(rIndex + 1, repos.size)
        }
        saveMeta(meta)
        saveNames(names)
        Log.i(TAG, "checkUpdates: ${updated.length()} updated")
        return JSONObject().apply { put("updated", updated); put("count", updated.length()) }
    }
}
