package com.saikou.sozo_tv.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns "an episode finished playing" into "AniList knows about it".
 *
 * Sits between the player and [AnilistRepository] because deciding WHETHER to
 * write is the hard part, not the write itself. Three rules govern it, and all
 * three exist to protect a list the user curated by hand:
 *
 *   1. never write to a title the user has not linked (or that did not match
 *      exactly) — a wrong media id silently rewrites the wrong show;
 *   2. never move progress backwards — a rewatch, or a phone that is further
 *      ahead, must not undo real progress;
 *   3. never surface a failure — this runs during playback, on a screen with no
 *      room for an error and no keyboard to answer it with.
 *
 * Mirrors the mobile app's tracker deliberately: the two write to one AniList
 * account, and a rule enforced on only one of them is not a rule.
 */
class AnilistTracker(
    private val repository: AnilistRepository,
    private val links: AnilistLinkStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /**
     * Titles already looked up and found to have no confident match, so a
     * hopeless auto-match is not retried on every single episode.
     */
    private val autoMatchFailed = HashSet<String>()

    val isConnected: Boolean get() = repository.isConnected

    /**
     * Makes sure the connection has been read from the account at least once.
     *
     * The player can reach an episode's 85% mark before any screen has asked the
     * account about AniList — on a cold start straight into playback from the
     * Home rail, that is the normal case, not an edge one. Without this the first
     * episode of every session would be silently dropped.
     */
    private suspend fun ensureConnected() {
        if (repository.connection.value is AnilistConnection.Unknown) {
            runCatching { repository.refresh() }
        }
    }

    /**
     * Fire-and-forget report from the player.
     *
     * Runs on the tracker's own scope, not the player's: the player is often
     * being torn down at exactly the moment the last episode completes, and a
     * coroutine on its scope would be cancelled before the request left the box.
     */
    fun reportEpisodeAsync(
        provider: String,
        contentId: String,
        title: String,
        episodeNumber: Int,
    ): Job = scope.launch {
        runCatching { reportEpisode(provider, contentId, title, episodeNumber) }
            .onFailure { Log.w(TAG, "report failed: ${it.message}") }
    }

    /**
     * Records that [episodeNumber] of a local title has been watched.
     *
     * Returns the media id it wrote to, or null when nothing was written — which
     * is the common and correct outcome for an unlinked title.
     */
    suspend fun reportEpisode(
        provider: String,
        contentId: String,
        title: String,
        episodeNumber: Int,
    ): Int? = mutex.withLock {
        if (episodeNumber <= 0 || contentId.isBlank()) return null
        ensureConnected()
        if (!isConnected) return null

        val mediaId = resolveMediaId(provider, contentId, title) ?: return null
        return if (write(mediaId, episodeNumber)) mediaId else null
    }

    /** An existing link first, then an exact title match. */
    private suspend fun resolveMediaId(
        provider: String,
        contentId: String,
        title: String,
    ): Int? {
        links.mediaIdFor(provider, contentId)?.let { return it }

        val key = AnilistLinkStore.keyFor(provider, contentId)
        if (key in autoMatchFailed) return null

        val match = findExactMatch(title)
        if (match == null) {
            autoMatchFailed.add(key)
            return null
        }

        links.save(
            AnilistTitleLink(
                provider = provider,
                contentId = contentId,
                mediaId = match.id,
                title = match.displayTitle,
                coverImage = match.coverImage,
                totalEpisodes = match.episodes,
                linkedAt = System.currentTimeMillis(),
                auto = true,
            )
        )
        return match.id
    }

    /**
     * Searches AniList and returns a result only when one of its titles matches
     * [title] EXACTLY once normalized.
     *
     * Deliberately strict. A fuzzy "best result" would attach season 2 to season
     * 1, or a recap film to the series, and then quietly write episode numbers
     * into it for months. When there is no exact match the user is asked instead
     * — being unlinked is recoverable, being wrongly linked is not obvious.
     */
    suspend fun findExactMatch(title: String): com.saikou.sozo_tv.data.remote.anilist.AnilistMedia? {
        val wanted = normalizeTitle(title)
        if (wanted.isEmpty()) return null
        return runCatching {
            repository.search(title).firstOrNull { media ->
                media.searchTitles.any { normalizeTitle(it) == wanted }
            }
        }.onFailure { Log.w(TAG, "auto-match failed for \"$title\": ${it.message}") }
            .getOrNull()
    }

    /** Reads the account's position, then writes only if this episode is genuinely ahead. */
    private suspend fun write(mediaId: Int, episodeNumber: Int): Boolean = try {
        val state = repository.entryState(mediaId)
        if (state != null && episodeNumber <= state.progress) {
            // Already at or beyond this episode — a rewatch, or the phone got
            // here first. Writing would move the list backwards.
            false
        } else {
            val result = repository.saveProgress(
                mediaId = mediaId,
                progress = episodeNumber,
                status = repository.statusFor(state?.status, episodeNumber, state?.totalEpisodes),
            )
            Log.d(TAG, "media $mediaId -> episode ${result.progress} (${result.status})")
            true
        }
    } catch (t: Throwable) {
        // Playback must not be disturbed by a tracker outage.
        Log.w(TAG, "write failed for media $mediaId: ${t.message}")
        false
    }

    companion object {
        private const val TAG = "AniListTracker"

        /**
         * Strips the noise that makes two names for the same show look different:
         * case, punctuation, and the release tags source sites append.
         */
        fun normalizeTitle(raw: String): String {
            var s = raw.lowercase().trim()
            NOISE.forEach { s = it.replace(s, " ") }
            // Punctuation is named rather than filtered with a Latin-only class:
            // these titles are also Cyrillic and Japanese, and `[^a-z0-9]` would
            // erase those scripts entirely and declare every one of them equal.
            s = PUNCTUATION.replace(s, " ")
            return s.trim().replace(WHITESPACE, " ")
        }

        /**
         * Bracketed tags (`[1080p]`, `(TV)`), quality markers and dub/sub labels —
         * all of which appear in source-site titles and never in an AniList one.
         */
        private val NOISE = listOf(
            Regex("""\[[^]]*]"""),
            Regex("""\([^)]*\)"""),
            Regex("""\b(1080p|720p|480p|4k|hd|fhd|bluray|bd|web-?dl|hevc|x26[45])\b"""),
            Regex("""\b(sub|dub|subbed|dubbed|uzbek|uzbekcha|tarjima|rus|russian)\b"""),
        )

        private val PUNCTUATION = Regex("""[.,:;!?'"`~@#${'$'}%^&*_+=<>/\\|{}\[\]()·・…—–-]+""")
        private val WHITESPACE = Regex("""\s+""")
    }
}
