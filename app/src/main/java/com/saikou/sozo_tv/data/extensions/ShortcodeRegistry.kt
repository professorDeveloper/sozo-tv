package com.saikou.sozo_tv.data.extensions

/**
 * Static catalogue mapping short, memorable codes to extension repo URLs.
 *
 * Repos are added by **shortcode** (not raw URL) from the Sources screen. The
 * curated entries below double as the on-screen "suggested" install chips — the
 * 2 first entries per group are the defaults shown prominently (mirrors Aniyomi's
 * recommended-repo cards). Extend the maps by hand to add more shortcodes.
 */
object ShortcodeRegistry {

    data class Entry(val code: String, val name: String, val url: String, val recommended: Boolean = false)

    /** Aniyomi anime repos (index.min.json). */
    val aniyomi: List<Entry> = listOf(
        Entry("yuzono", "Yuzono Anime", "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json", recommended = true),
        Entry("secozzi", "Secozzi", "https://raw.githubusercontent.com/Secozzi/aniyomi-extensions/repo/index.min.json", recommended = true),
    )

    /** CloudStream repos (repo.json). */
    val cloudstream: List<Entry> = listOf(
        Entry("phisher", "Phisher Extensions", "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json", recommended = true),
        Entry("redowan", "Redowan CloudStream", "https://raw.githubusercontent.com/redowan99/Redowan-CloudStream/master/repo.json", recommended = true),
    )

    fun entries(group: String): List<Entry> = when (group) {
        ExtGroup.ANIYOMI -> aniyomi
        ExtGroup.CLOUDSTREAM -> cloudstream
        else -> emptyList()
    }

    /** Resolve a shortcode (case-insensitive) within a group to its repo URL. */
    fun resolve(group: String, code: String): String? {
        val c = code.trim().lowercase()
        return entries(group).firstOrNull { it.code == c }?.url
    }
}

object ExtGroup {
    const val ANIYOMI = "aniyomi"
    const val CLOUDSTREAM = "cloudstream"
    const val SERVER = "server"
}
