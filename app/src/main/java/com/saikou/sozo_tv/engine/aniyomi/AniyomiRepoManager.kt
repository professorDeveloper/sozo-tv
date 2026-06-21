package com.saikou.sozo_tv.engine.aniyomi

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AniyomiRepoManager(private val context: Context, private val host: AniyomiHost) {

    companion object {
        private const val TAG = "AniyomiRepo"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }

    private val prefs = context.getSharedPreferences("aniyomi", Context.MODE_PRIVATE)
    private val mangaRegex =
        Regex("manga|manhwa|manhua|doujin|comic|webtoon|novel", RegexOption.IGNORE_CASE)

    @Volatile private var ensured = false

    private fun isManga(name: String) = mangaRegex.containsMatchIn(name)

    private fun savedRepos(): MutableList<String> {
        val raw = prefs.getString("repos", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw); MutableList(arr.length()) { arr.getString(it) }
        } catch (_: Throwable) {
            mutableListOf()
        }
    }

    private fun persist(repos: List<String>) {
        prefs.edit().putString("repos", JSONArray(repos).toString()).apply()
    }

    private fun loadMeta(): JSONObject =
        try { JSONObject(prefs.getString("meta", "{}") ?: "{}") } catch (_: Throwable) { JSONObject() }

    private fun saveMeta(o: JSONObject) {
        prefs.edit().putString("meta", o.toString()).apply()
    }

    private fun loadNames(): JSONObject =
        try { JSONObject(prefs.getString("names", "{}") ?: "{}") } catch (_: Throwable) { JSONObject() }

    private fun saveNames(o: JSONObject) {
        prefs.edit().putString("names", o.toString()).apply()
    }

    fun ensureLoaded() {
        if (ensured) return
        ensured = true
        val meta = loadMeta()
        val names = loadNames()
        for (repo in savedRepos()) {
            val repoName = names.optString(repo).ifEmpty { fallbackName(repo) }
            val entries = meta.optJSONArray(repo) ?: continue
            for (i in 0 until entries.length()) {
                val e = entries.optJSONObject(i) ?: continue
                host.registerMeta(e, repoName)
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
            val ids = (0 until entries.length())
                .mapNotNull { entries.optJSONObject(it)?.optString("id") }
                .filter { it.isNotEmpty() }
            host.removeSources(ids)
            meta.remove(key); saveMeta(meta)
        }
        val names = loadNames(); names.remove(key); saveNames(names)
        val repos = savedRepos(); repos.remove(key); persist(repos)
        return JSONObject().apply { put("repos", JSONArray(repos)) }.toString()
    }

    fun addRepo(input: String, progress: ((Int, Int) -> Unit)? = null): JSONObject {
        val result = addRepoInternal(input, progress)
        if (result.optInt("sourceCount") > 0) {
            val repos = savedRepos()
            val v = input.trim()
            if (!repos.contains(v)) { repos.add(v); persist(repos) }
        }
        return result
    }

    private fun addRepoInternal(input: String, progress: ((Int, Int) -> Unit)? = null): JSONObject {
        val repoUrl = input.trim()
        val body = httpGet(repoUrl)
        val packages = try { JSONArray(body ?: "[]") } catch (_: Throwable) { JSONArray() }
        val repoName = fallbackName(repoUrl)
        val metaEntries = JSONArray()
        val providers = JSONArray()
        var sourceCount = 0

        val iconBase = repoUrl.substringBeforeLast('/')
        val total = packages.length()
        progress?.invoke(0, total)
        for (i in 0 until total) {
            val pkg = packages.optJSONObject(i) ?: continue
            val apkName = pkg.optString("apk")
            val pkgName = pkg.optString("pkg")
            val nsfw = pkg.optInt("nsfw", 0) == 1
            val apkRemote = if (apkName.isEmpty()) "" else apkUrl(repoUrl, apkName)
            val iconRemote = if (pkgName.isEmpty()) "" else "$iconBase/icon/$pkgName.png"
            val sources = pkg.optJSONArray("sources") ?: JSONArray()
            for (j in 0 until sources.length()) {
                val src = sources.optJSONObject(j) ?: continue
                val name = src.optString("name")
                if (isManga(name)) continue
                val entry = JSONObject().apply {
                    put("id", src.optString("id"))
                    put("name", name)
                    put("lang", src.optString("lang"))
                    put("baseUrl", src.optString("baseUrl"))
                    put("pkg", pkgName)
                    put("className", pkg.optString("name"))
                    put("apkUrl", apkRemote)
                    put("iconUrl", iconRemote)
                    put("nsfw", nsfw)
                }
                metaEntries.put(entry)
                host.registerMeta(entry, repoName)
                providers.put(name)
                sourceCount++
            }
            progress?.invoke(i + 1, total)
        }

        val meta = loadMeta(); meta.put(input.trim(), metaEntries); saveMeta(meta)
        if (sourceCount > 0) {
            val names = loadNames(); names.put(input.trim(), repoName); saveNames(names)
        }
        Log.i(TAG, "addRepo($repoUrl): $sourceCount sources")
        return JSONObject().apply {
            put("repo", repoUrl); put("sourceCount", sourceCount); put("providers", providers)
        }
    }

    private fun apkUrl(repoUrl: String, apk: String): String {
        val base = repoUrl.substringBeforeLast('/')
        return "$base/apk/$apk"
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
    } catch (t: Throwable) {
        Log.e(TAG, "GET $url failed: ${t.message}"); null
    }

    private fun fallbackName(url: String): String {
        val gh = Regex("github(?:usercontent)?\\.com/([^/]+)/([^/]+)").find(url)
        if (gh != null) return "${gh.groupValues[1]}/${gh.groupValues[2]}"
        return try { URL(url).host ?: url } catch (_: Throwable) { url }
    }
}
