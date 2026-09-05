package com.saikou.sozo_tv.utils

/**
 * How well a search result answers the query that produced it.
 *
 * ## Why the client has to do this at all
 *
 * A search here fans out to third-party catalogues, and several of them match
 * on more than the title — a description, a tag list, an alias field. That is
 * usually a feature: it is how "one piece" finds *Van Pis*, the Uzbek dub,
 * whose title shares not one letter with the query. It is also how "naruto"
 * finds *Learn To Draw APK* and how "aot" fills a row with unrelated films,
 * because three letters occur inside a word somewhere in a synopsis.
 *
 * Verified against the live backend on 2026-08-31: the default provider
 * returned 18 unrelated titles for "aot" and one for "naruto", while the anime
 * providers returned the right show for both.
 *
 * ## Rank, do not filter
 *
 * Scoring orders results; a zero score never removes a row on its own. *Van
 * Pis* scores zero against "one piece" and is the single most correct result in
 * the set — a relevance filter would delete exactly the results that justify
 * searching many sources. Dropping is reserved for [looksUnsearched], which
 * fires on a different signal entirely.
 *
 * This is the Kotlin twin of the app's `SearchRelevance`; the two are expected
 * to agree, so a change to one belongs in the other.
 */
object SearchRelevance {

    /**
     * Words that carry no signal in a title match. Deliberately short: a long
     * stop list starts eating real title words, and a useless token only costs
     * a diluted score.
     */
    private val STOP_WORDS = setOf(
        "the", "and", "a", "an", "of", "season", "episode", "part",
        "sub", "subbed", "dub", "dubbed", "watch", "online", "free",
    )

    /**
     * A batch this size or larger with nothing matching is a catalogue dump.
     *
     * The number separates the two shapes seen in practice. A source that
     * searched and found only an aliased match returns one or two rows; a
     * source that ignored the query returns a full page — 16, 18, 26 rows of
     * whatever it lists by default.
     */
    private const val DUMP_SIZE = 6

    /**
     * A token has to be this long before a substring match counts for anything.
     * "aot" is the worked example: three letters land inside ordinary words in
     * every language.
     */
    private const val MIN_SUBSTRING_TOKEN = 4

    private val NON_WORD = Regex("[^a-z0-9\\u0400-\\u04ff\\u0600-\\u06ff]+")

    /** Lowercased, punctuation dropped, whitespace collapsed. Cyrillic and
     *  Arabic survive so a Russian or Persian title stays comparable. */
    fun normalize(text: String): String =
        text.lowercase().replace(NON_WORD, " ").trim().replace(Regex("\\s+"), " ")

    /** Query words a genuine title match would plausibly contain. Empty for a
     *  query in a script this cannot tokenise, which disables scoring rather
     *  than ranking everything at zero. */
    fun tokensOf(query: String): Set<String> =
        normalize(query).split(' ')
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()

    /**
     * 0.0 (nothing in the title relates to the query) to 1.0 (it is the query).
     *
     * The bands are ordered by how sure the match is: an exact title beats a
     * prefix beats a substring beats scattered words. Within the last band a
     * whole word counts fully and a substring a quarter, because "titan" inside
     * "Attack on Titan" is evidence and "aot" inside "Bo'zqir" is a coincidence.
     */
    fun score(title: String?, query: String): Double {
        val t = normalize(title.orEmpty())
        val q = normalize(query)
        if (t.isEmpty() || q.isEmpty()) return 0.0
        if (t == q) return 1.0
        if (t.startsWith("$q ")) return 0.9
        if (t.contains(q)) return 0.8

        val tokens = tokensOf(query)
        if (tokens.isEmpty()) return 0.0
        val words = t.split(' ').toSet()

        var hits = 0.0
        for (token in tokens) {
            when {
                words.contains(token) -> hits += 1.0
                token.length >= MIN_SUBSTRING_TOKEN && t.contains(token) -> hits += 0.25
            }
        }
        if (hits == 0.0) return 0.0
        // Capped below the substring band so a partial word match can never
        // outrank a title that literally contains the query.
        return 0.1 + 0.65 * (hits / tokens.size)
    }

    /**
     * Reorders results best-first, keeping the source's own order where scores
     * tie.
     *
     * Stability matters on a television more than anywhere: the grid repaints
     * as other sources answer, and a row that reshuffles under a D-pad moves
     * the focused card out from under the remote mid-press.
     */
    fun <T> rank(items: List<T>, query: String, titleOf: (T) -> String?): List<T> {
        if (items.size < 2 || normalize(query).isEmpty()) return items
        val scored = items.mapIndexed { i, item -> Triple(i, item, score(titleOf(item), query)) }
        if (scored.all { it.third == scored.first().third }) return items
        return scored
            .sortedWith(compareByDescending<Triple<Int, T, Double>> { it.third }.thenBy { it.first })
            .map { it.second }
    }

    /** The best score in a batch — how well this source answered, in one number. */
    fun <T> bestScore(items: List<T>, query: String, titleOf: (T) -> String?): Double =
        items.maxOfOrNull { score(titleOf(it), query) } ?: 0.0

    /**
     * Whether a source plainly did not search, and returned its front page.
     *
     * Two conditions, and both are needed. Nothing matching is not enough on
     * its own — that is also what an alias-only match looks like, and dropping
     * those would remove the best results in the app. A full page of rows is
     * not enough either; a popular query legitimately fills a page.
     */
    fun <T> looksUnsearched(items: List<T>, query: String, titleOf: (T) -> String?): Boolean {
        if (items.size < DUMP_SIZE) return false
        if (tokensOf(query).isEmpty()) return false
        return bestScore(items, query, titleOf) == 0.0
    }
}
