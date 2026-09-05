package com.saikou.sozo_tv.utils

import com.saikou.sozo_tv.domain.model.SearchModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every fixture here is a real response from the app's own backend, captured
 * while chasing "search doesn't work". The junk is not invented.
 */
class SearchRelevanceTest {

    private fun m(title: String) =
        SearchModel(id = title.hashCode(), title = title, image = null,
            studios = null, genres = null, averageScore = null)

    private fun all(vararg titles: String) = titles.map(::m)

    private fun rank(items: List<SearchModel>, q: String) =
        SearchRelevance.rank(items, q) { it.title }

    private fun dump(items: List<SearchModel>, q: String) =
        SearchRelevance.looksUnsearched(items, q) { it.title }

    @Test
    fun `the exact title wins outright`() {
        assertEquals(1.0, SearchRelevance.score("Naruto", "naruto"), 0.0001)
        assertEquals(1.0, SearchRelevance.score("Naruto: Shippuden", "naruto shippuden"), 0.0001)
    }

    @Test
    fun `a prefix beats a mere containment`() {
        val starts = SearchRelevance.score("Naruto Shippuden", "naruto")
        val contains = SearchRelevance.score("Boruto: Naruto Next Generations", "naruto")
        assertTrue(starts > contains)
        assertTrue(contains > 0.0)
    }

    @Test
    fun `a short query is not allowed to match inside a word`() {
        // "aot" returned 18 unrelated Uzbek films from the live backend.
        assertEquals(0.0, SearchRelevance.score("Bo'zqir", "aot"), 0.0001)
        assertEquals(0.0, SearchRelevance.score("Moxir oshpaz", "aot"), 0.0001)
        assertTrue(SearchRelevance.score("AOT Chronicles", "aot") > 0.0)
    }

    @Test
    fun `an unrelated title scores nothing`() {
        assertEquals(0.0, SearchRelevance.score("Learn To Draw APK", "naruto"), 0.0001)
    }

    @Test
    fun `the real match comes first even when it arrived last`() {
        val ranked = rank(
            all(
                "Naruto the Movie 2: Legend of the Stone of Gelel",
                "Boruto: Naruto Next Generations",
                "Naruto",
            ),
            "naruto",
        )
        assertEquals("Naruto", ranked.first().title)
    }

    @Test
    fun `junk sinks but is never dropped`() {
        // "Van Pis" is the Uzbek dub of One Piece and shares not one letter
        // with the query. It scores zero and is the best result in the set,
        // which is exactly why ranking must not become filtering.
        val ranked = rank(all("Learn To Draw APK", "Van Pis", "One Piece"), "one piece")
        assertEquals("One Piece", ranked.first().title)
        assertEquals(3, ranked.size)
        assertTrue(ranked.any { it.title == "Van Pis" })
    }

    @Test
    fun `equal scores keep the source order`() {
        // The grid repaints as other sources answer; a row that reshuffles
        // moves the focused card out from under the remote mid-press.
        val titles = listOf("Naruto A", "Naruto B", "Naruto C")
        assertEquals(titles, rank(titles.map(::m), "naruto").map { it.title })
    }

    @Test
    fun `a full page with nothing matching is a catalogue dump`() {
        assertTrue(
            dump(
                all(
                    "Narkoz", "Hech narsa tasodif emas", "Hayotda hamma narsa bo'ladi",
                    "Million dollarlik tuzoq", "Senga bo'lgan muhabbat",
                    "Choson nikoh agentligi", "Yolg'on hayot", "Qora qish",
                ),
                "naruto",
            ),
        )
    }

    @Test
    fun `one aliased match is not a dump`() {
        // The distinction the whole guard rests on: a source that searched and
        // found a differently-titled match returns one or two rows, never a
        // full page.
        assertFalse(dump(all("Van Pis"), "one piece"))
        assertFalse(dump(all("Van Pis", "Learn To Draw APK"), "one piece"))
    }

    @Test
    fun `a page that does match is left alone`() {
        // "batman" legitimately fills a page on the same provider that dumps
        // its catalogue for "naruto". Size alone must never be the trigger.
        assertFalse(
            dump(
                all(
                    "Batman: The Enemy Within", "Batman: Jimjitlik HD 2019",
                    "Бэтмен (2022)", "Betmen: Batman Supermenga qarshi",
                    "Batman Begins", "The Batman",
                ),
                "batman",
            ),
        )
    }

    @Test
    fun `a query with no scoreable words disables the guard`() {
        assertFalse(dump(all("A", "B", "C", "D", "E", "F", "G"), "。。。"))
    }
}
