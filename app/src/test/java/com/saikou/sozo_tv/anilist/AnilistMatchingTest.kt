package com.saikou.sozo_tv.anilist

import com.saikou.sozo_tv.data.remote.anilist.AnilistAiring
import com.saikou.sozo_tv.data.remote.anilist.AnilistListEntry
import com.saikou.sozo_tv.data.remote.anilist.AnilistMedia
import com.saikou.sozo_tv.data.remote.anilist.AnilistStatus
import com.saikou.sozo_tv.data.repository.AnilistLinkStore
import com.saikou.sozo_tv.data.repository.AnilistTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnilistMatchingTest {

    @Test
    fun `ignores case and punctuation`() {
        assertEquals(
            AnilistTracker.normalizeTitle("Fate/stay night: Unlimited Blade Works"),
            AnilistTracker.normalizeTitle("fate stay night unlimited blade works"),
        )
    }

    @Test
    fun `strips the release tags source sites append`() {
        assertEquals(
            AnilistTracker.normalizeTitle("Naruto Shippuden [1080p] (Uzbek tarjima)"),
            AnilistTracker.normalizeTitle("Naruto Shippuden"),
        )
    }

    @Test
    fun `keeps non-Latin scripts intact`() {
        assertTrue(AnilistTracker.normalizeTitle("鋼の錬金術師").isNotEmpty())
        assertFalse(
            AnilistTracker.normalizeTitle("鋼の錬金術師") ==
                AnilistTracker.normalizeTitle("進撃の巨人")
        )
    }

    @Test
    fun `collapses whitespace rather than leaving gaps behind`() {
        assertEquals("one piece", AnilistTracker.normalizeTitle("  One   Piece  "))
    }

    @Test
    fun `different seasons do not collapse into one another`() {
        assertFalse(
            AnilistTracker.normalizeTitle("Attack on Titan") ==
                AnilistTracker.normalizeTitle("Attack on Titan Season 2")
        )
    }

    @Test
    fun `link key is case-insensitive so one title is not linked twice`() {
        assertEquals(
            AnilistLinkStore.keyFor("cs:AnimePahe", "ABC123"),
            AnilistLinkStore.keyFor("cs:animepahe", "abc123"),
        )
    }

    @Test
    fun `link key separates the same content on different providers`() {
        assertFalse(
            AnilistLinkStore.keyFor("cs:A", "1") == AnilistLinkStore.keyFor("an:B", "1")
        )
    }

    private fun entry(
        progress: Int,
        episodes: Int? = null,
        airing: AnilistAiring? = null,
    ) = AnilistListEntry(
        id = 1,
        media = AnilistMedia(id = 1, episodes = episodes, nextAiring = airing),
        status = AnilistStatus.CURRENT.value,
        progress = progress,
    )

    @Test
    fun `offers the next episode while there are unwatched ones`() {
        assertEquals(4, entry(progress = 3, episodes = 12).nextEpisode)
    }

    @Test
    fun `offers nothing once the series is finished`() {
        assertNull(entry(progress = 12, episodes = 12).nextEpisode)
    }

    @Test
    fun `caps at what has aired, not at the announced total`() {
        val e = entry(
            progress = 5,
            episodes = 24,
            airing = AnilistAiring(episode = 6, airingAt = 4_102_444_800L),
        )
        assertNull(e.nextEpisode)
        assertEquals(0, e.behindBy)
    }

    @Test
    fun `counts how far behind the viewer is on an airing show`() {
        val e = entry(
            progress = 2,
            episodes = 24,
            airing = AnilistAiring(episode = 6, airingAt = 4_102_444_800L),
        )
        assertEquals(3, e.behindBy)
        assertEquals(3, e.nextEpisode)
    }

    @Test
    fun `completion is null when the total is unknown`() {
        assertNull(entry(progress = 4).completion)
        assertEquals(0.5f, entry(progress = 6, episodes = 12).completion!!, 0.0001f)
    }

    @Test
    fun `airing time is read as seconds, not milliseconds`() {
        val airing = AnilistAiring(episode = 1, airingAt = 1_700_000_000L)
        assertEquals(1_700_000_000_000L, airing.airsAtMillis)
    }

    @Test
    fun `an episode in the past has aired`() {
        assertTrue(AnilistAiring(episode = 1, airingAt = 1_000_000_000L).hasAired)
    }

    @Test
    fun `searchTitles drops blanks and duplicates, best guess first`() {
        val media = AnilistMedia(
            id = 1,
            englishTitle = "Bleach",
            romajiTitle = "Bleach",
            nativeTitle = "ブリーチ",
        )
        assertEquals(listOf("Bleach", "ブリーチ"), media.searchTitles)
    }

    @Test
    fun `displayTitle falls back when English is missing`() {
        assertEquals(
            "Shingeki no Kyojin",
            AnilistMedia(id = 1, romajiTitle = "Shingeki no Kyojin").displayTitle,
        )
    }
}
