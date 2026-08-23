package com.saikou.sozo_tv.data.repository

import android.util.Log
import com.saikou.sozo_tv.data.remote.anilist.AnilistGraphQlClient
import com.saikou.sozo_tv.data.remote.mal.MalConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns "an episode finished playing" into "MyAnimeList knows about it".
 *
 * Governed by the same three rules as [AnilistTracker]: never write to a title
 * that was not matched confidently, never move progress backwards, never
 * surface a failure during playback.
 *
 * What differs is how the id is found. MAL's own search is poor at the
 * translated and transliterated titles Sozo's sources carry, and matching the
 * same show twice — once per tracker — would double both the work and the
 * number of ways it can be wrong. AniList already solves this and publishes the
 * MAL id for the same entry, so the AniList side is reused as a MATCHER even
 * when AniList itself is not connected: its search and media reads are public.
 */
class MalTracker(
    private val repository: MalRepository,
    private val links: MalLinkStore,
    private val anilistLinks: AnilistLinkStore,
    private val anilistTracker: AnilistTracker,
    private val anilistApi: AnilistGraphQlClient,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val autoMatchFailed = HashSet<String>()

    val isConnected: Boolean get() = repository.isConnected

    private suspend fun ensureConnected() {
        if (repository.connection.value is MalConnection.Unknown) {
            runCatching { repository.refresh() }
        }
    }

    fun reportEpisodeAsync(
        provider: String,
        contentId: String,
        title: String,
        episodeNumber: Int,
    ): Job = scope.launch {
        runCatching { reportEpisode(provider, contentId, title, episodeNumber) }
            .onFailure { Log.w(TAG, "report failed: ${it.message}") }
    }

    suspend fun reportEpisode(
        provider: String,
        contentId: String,
        title: String,
        episodeNumber: Int,
    ): Int? = mutex.withLock {
        if (episodeNumber <= 0 || contentId.isBlank()) return null
        ensureConnected()
        if (!isConnected) return null

        val animeId = resolveAnimeId(provider, contentId, title) ?: return null
        return if (write(animeId, episodeNumber)) animeId else null
    }

    /**
     * Finds the MAL id for a local title.
     *
     * In order: a MAL link already made; the AniList link the USER made by hand
     * on their phone; an automatic AniList match.
     *
     * The second step is the one that matters most and is easiest to leave out.
     * A manual AniList link exists precisely because no search could match that
     * title — an Uzbek dub, a transliteration, a source's own naming. Skipping
     * to matching would re-run the search that already failed and quietly track
     * nothing for exactly the titles the user took the trouble to link.
     */
    private suspend fun resolveAnimeId(
        provider: String,
        contentId: String,
        title: String,
    ): Int? {
        links.mediaIdFor(provider, contentId)?.let { return it }

        val key = MalLinkStore.keyFor(provider, contentId)
        if (key in autoMatchFailed) return null

        var animeId: Int? = null
        var linkTitle = title
        var cover: String? = null
        var total: Int? = null

        val linked = anilistLinks.get(provider, contentId)
        if (linked != null && linked.mediaId > 0) {
            animeId = malIdFor(linked.mediaId)
            if (animeId != null) {
                linkTitle = linked.title.ifBlank { title }
                cover = linked.coverImage
                total = linked.totalEpisodes
            }
        }

        // No hand-made link, or AniList has no MAL counterpart for the one there
        // is. Fall back to matching by title — the same strict exact-match rule,
        // because a fuzzy best-result would attach season 2 to season 1 and then
        // write into it for months.
        if (animeId == null) {
            val match = anilistTracker.findExactMatch(title)
            animeId = match?.idMal
            if (animeId != null && match != null) {
                linkTitle = match.displayTitle
                cover = match.coverImage
                total = match.episodes
            }
        }

        if (animeId == null) {
            autoMatchFailed.add(key)
            return null
        }

        links.save(
            TrackerTitleLink(
                provider = provider,
                contentId = contentId,
                mediaId = animeId,
                title = linkTitle,
                coverImage = cover,
                totalEpisodes = total,
                linkedAt = System.currentTimeMillis(),
                auto = true,
            )
        )
        return animeId
    }

    /**
     * AniList media id -> MAL anime id, swallowing a lookup that cannot be made.
     *
     * A failure here falls through to matching rather than aborting: being
     * offline for one request is not a reason to stop tracking a title forever.
     */
    private suspend fun malIdFor(anilistMediaId: Int): Int? = runCatching {
        anilistApi.malIdFor(anilistMediaId)
    }.onFailure {
        Log.w(TAG, "idMal lookup failed for AniList $anilistMediaId: ${it.message}")
    }.getOrNull()

    private suspend fun write(animeId: Int, episodeNumber: Int): Boolean = try {
        val state = repository.entryState(animeId)
        if (state != null && episodeNumber <= state.watchedEpisodes) {
            // Already at or beyond this episode — a rewatch, or another device
            // got here first. Writing would move the list backwards.
            false
        } else {
            val result = repository.saveProgress(
                animeId = animeId,
                episodes = episodeNumber,
                status = repository.statusFor(
                    current = state?.status,
                    isRewatching = state?.isRewatching ?: false,
                    episode = episodeNumber,
                    total = state?.totalEpisodes,
                ),
            )
            Log.d(TAG, "anime $animeId -> episode ${result.watchedEpisodes} (${result.status})")
            true
        }
    } catch (t: Throwable) {
        Log.w(TAG, "write failed for anime $animeId: ${t.message}")
        false
    }

    private companion object {
        const val TAG = "MalTracker"
    }
}
