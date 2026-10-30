package com.saikou.sozo_tv.engine.server

import org.json.JSONArray
import org.json.JSONObject

/** The audio languages a server provider offers per episode, and one payload holding them all. */
internal object MediaLanguages {

    private val QUALITY = Regex("""(?i)(\d{3,4}p?|4k|hd|fhd|sd|auto)""")

    /**
     * An episode's `availableLangs`: "sub" first, as the phone app does, then the provider's
     * order. Some providers (uzmovi) list their servers' qualities there instead; those are
     * left out, so such an episode is resolved once, as before.
     */
    fun of(episode: JSONObject): List<String> {
        val arr = episode.optJSONArray("availableLangs") ?: return emptyList()
        val langs = (0 until arr.length()).map { arr.optString(it).trim() }
            .filter { it.isNotEmpty() && it != "null" && !QUALITY.matches(it) }
            .distinct()
        return langs.sortedBy { if (it.equals("sub", ignoreCase = true)) 0 else 1 }
    }

    fun label(lang: String): String = when (lang.lowercase()) {
        "sub" -> "Sub"
        "dub" -> "Dub"
        "softsub" -> "Softsub"
        "hardsub" -> "Hardsub"
        "raw" -> "Raw"
        else -> lang
    }

    /**
     * One media payload holding every language's sources, each tagged with its language as the
     * server name. [resolved] pairs a label with an already translated payload, first language
     * first. That payload supplies the media-level fields, so its headers are copied onto each
     * source: the player applies only the first payload's.
     */
    fun merge(resolved: List<Pair<String, String>>): String {
        val base = JSONObject(resolved.first().second)
        val sources = JSONArray()
        val subtitles = JSONArray()
        val subtitleFiles = mutableSetOf<String>()
        for ((index, entry) in resolved.withIndex()) {
            val (label, payload) = entry
            val media = JSONObject(payload)
            val mediaHeaders = media.optJSONObject("headers") ?: JSONObject()
            val srcs = media.optJSONArray("videoSources") ?: JSONArray()
            for (i in 0 until srcs.length()) {
                val s = srcs.optJSONObject(i) ?: continue
                val headers = JSONObject(mediaHeaders.toString())
                s.optJSONObject("headers")?.let { own -> own.keys().forEach { headers.put(it, own.get(it)) } }
                s.put("headers", headers)
                s.put("host", label)
                s.put("isDefault", index == 0 && s.optBoolean("isDefault", i == 0))
                sources.put(s)
            }
            val subs = media.optJSONArray("subtitles") ?: JSONArray()
            for (i in 0 until subs.length()) {
                val t = subs.optJSONObject(i) ?: continue
                if (!subtitleFiles.add(t.optString("file"))) continue
                // Only the first language's subtitles may switch themselves on; a dub must
                // not start with another language's captions over it.
                if (index > 0) t.put("default", false)
                subtitles.put(t)
            }
        }
        return base.put("videoSources", sources).put("subtitles", subtitles).toString()
    }
}
