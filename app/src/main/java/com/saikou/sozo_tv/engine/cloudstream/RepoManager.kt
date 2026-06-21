package com.saikou.sozo_tv.engine.cloudstream

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

    companion object {
        private const val TAG = "CloudStreamRepo"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
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
    fun ensureLoaded() {
        if (ensured) return
        ensured = true
        val meta = loadMeta()
        val names = loadNames()
        for (repo in savedRepos()) {
            val repoName = names.optString(repo).ifEmpty { fallbackName(repo) }
            val entries = meta.optJSONArray(repo)
            if (entries == null) {
                // Legacy repo without metadata → load once (also persists metadata).
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
            host.removeProviders(providers)
            meta.remove(key); saveMeta(meta)
        }
        val names = loadNames(); names.remove(key); saveNames(names)
        val repos = savedRepos()
        repos.remove(key)
        persist(repos)
        return JSONObject().apply { put("repos", JSONArray(repos)) }.toString()
    }

    private fun httpGet(url: String): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; instanceFollowRedirects = true
            connectTimeout = 20000; readTimeout = 30000
            setRequestProperty("User-Agent", UA)
        }
        val code = conn.responseCode
        if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
        else { Log.e(TAG, "GET $url -> $code"); null }
    } catch (t: Throwable) { Log.e(TAG, "GET $url failed: ${t.message}"); null }

    private data class RepoInfo(val name: String?, val pluginListUrls: List<String>)

    /** repo.json {name, pluginLists:[...]} OR a direct plugins.json array. */
    private fun fetchRepo(repoUrl: String): RepoInfo {
        val body = httpGet(repoUrl) ?: return RepoInfo(null, emptyList())
        val trimmed = body.trimStart()
        if (trimmed.startsWith("[")) return RepoInfo(null, listOf(repoUrl)) // direct plugins.json
        return try {
            val o = JSONObject(body)
            val name = o.optString("name").ifEmpty { null }
            val arr = o.optJSONArray("pluginLists")
            val urls = if (arr != null) (0 until arr.length()).map { arr.getString(it) } else emptyList()
            RepoInfo(name, urls)
        } catch (t: Throwable) { Log.e(TAG, "parse repo.json failed: ${t.message}"); RepoInfo(null, emptyList()) }
    }

    // Persisted display names: { repoInput: "Repo Name" }
    private fun loadNames(): JSONObject =
        try { JSONObject(prefs.getString("names", "{}") ?: "{}") } catch (_: Throwable) { JSONObject() }
    private fun saveNames(o: JSONObject) { prefs.edit().putString("names", o.toString()).apply() }

    private fun downloadCs3(internalName: String, version: Int, url: String): File? {
        val file = File(cs3Dir, "$internalName@$version.cs3")
        if (file.exists() && file.length() > 0) return file
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; instanceFollowRedirects = true
                connectTimeout = 20000; readTimeout = 60000
                setRequestProperty("User-Agent", UA)
            }
            if (conn.responseCode !in 200..299) { Log.e(TAG, "cs3 $url -> ${conn.responseCode}"); return null }
            conn.inputStream.use { input -> file.outputStream.use { input.copyTo(it) } }
            // drop stale versions of the same plugin
            cs3Dir.listFiles()?.forEach { f ->
                if (f.name.startsWith("$internalName@") && f.name != file.name) f.delete()
            }
            file
        } catch (t: Throwable) { Log.e(TAG, "download cs3 failed: ${t.message}"); null }
    }

    private val File.outputStream get() = java.io.FileOutputStream(this)

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

    private data class PluginRef(
        val url: String, val internalName: String, val version: Int, val iconUrl: String?,
    )

    private fun addRepoInternal(input: String, progress: ((Int, Int) -> Unit)? = null): JSONObject {
        val repoUrl = input.trim()
        val info = fetchRepo(repoUrl)
        val providers = JSONArray()
        val metaEntries = JSONArray()
        var pluginCount = 0

        // Gather every plugin descriptor first so the total is known up front and
        // we can report "downloaded N / M" progress to the install UI.
        val all = ArrayList<PluginRef>()
        for (listUrl in info.pluginListUrls) {
            val body = httpGet(listUrl) ?: continue
            val plugins = try { JSONArray(body) } catch (t: Throwable) { continue }
            for (i in 0 until plugins.length()) {
                val p = plugins.optJSONObject(i) ?: continue
                val url = p.optString("url"); if (url.isEmpty()) continue
                val internalName = p.optString("internalName").ifEmpty { p.optString("name", "plugin$i") }
                val version = if (p.has("version")) p.optInt("version") else 0
                val iconUrl = p.optString("iconUrl").ifEmpty { null }
                all.add(PluginRef(url, internalName, version, iconUrl))
            }
        }

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
        // Persist this repo's provider metadata for lazy loading on next launch.
        val meta = loadMeta(); meta.put(input.trim(), metaEntries); saveMeta(meta)
        // Persist a friendly display name (from repo.json, else derived from url).
        if (pluginCount > 0) {
            val names = loadNames(); names.put(input.trim(), info.name ?: fallbackName(repoUrl)); saveNames(names)
        }
        Log.i(TAG, "addRepo($repoUrl): $pluginCount plugins, providers=$providers")
        return JSONObject().apply {
            put("repo", repoUrl); put("pluginCount", pluginCount); put("providers", providers)
        }
    }

    /**
     * Re-fetch every saved repo and re-download any plugin whose repo version is
     * newer than the installed one (parsed from the cached `@<version>.cs3` name).
     * Updates metadata + reloads the plugin. Returns the updated provider names.
     */
    fun checkUpdates(progress: ((Int, Int) -> Unit)? = null): JSONObject {
        val meta = loadMeta()
        val names = loadNames()
        val updated = JSONArray()
        val repos = savedRepos()
        progress?.invoke(0, repos.size)
        for ((rIndex, repo) in repos.withIndex()) {
            val info = fetchRepo(repo)
            val latest = HashMap<String, PluginRef>()
            for (listUrl in info.pluginListUrls) {
                val body = httpGet(listUrl) ?: continue
                val plugins = try { JSONArray(body) } catch (_: Throwable) { continue }
                for (i in 0 until plugins.length()) {
                    val p = plugins.optJSONObject(i) ?: continue
                    val url = p.optString("url"); if (url.isEmpty()) continue
                    val internalName = p.optString("internalName").ifEmpty { p.optString("name", "plugin$i") }
                    val version = if (p.has("version")) p.optInt("version") else 0
                    val iconUrl = p.optString("iconUrl").ifEmpty { null }
                    latest[internalName] = PluginRef(url, internalName, version, iconUrl)
                }
            }
            val repoName = names.optString(repo).ifEmpty { info.name ?: fallbackName(repo) }
            val entries = meta.optJSONArray(repo)
            if (entries != null) {
                val downloaded = HashSet<String>()
                for (i in 0 until entries.length()) {
                    val e = entries.optJSONObject(i) ?: continue
                    val internalName = e.optString("internalName")
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
            }
            progress?.invoke(rIndex + 1, repos.size)
        }
        saveMeta(meta)
        Log.i(TAG, "checkUpdates: ${updated.length()} updated")
        return JSONObject().apply { put("updated", updated); put("count", updated.length()) }
    }
}
