package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.extensions.ExtDetail
import com.saikou.sozo_tv.data.extensions.ExtensionContentRegistry
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.extensions.toCast
import com.saikou.sozo_tv.data.extensions.toDetailModel
import com.saikou.sozo_tv.data.extensions.toMainModel
import com.saikou.sozo_tv.domain.model.Cast
import com.saikou.sozo_tv.domain.model.CastDetailModel
import com.saikou.sozo_tv.domain.model.DetailModel
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.domain.repository.DetailRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Detail/cast/relations all derive from a single `ExtensionEngine.load()` payload.
 * The synthetic media `id` is resolved back to its `(provider,url)` via
 * [ExtensionContentRegistry], loaded once, and cached so cast/relations re-use it.
 */
class DetailRepositoryImpl(
    private val engine: ExtensionEngine,
    private val links: AnilistLinkStore,
    private val sourceRegistry: AnilistSourceRegistry,
) : DetailRepository {

    private val cache = ConcurrentHashMap<Int, ExtDetail>()

    private suspend fun loadDetail(id: Int): ExtDetail? {
        cache[id]?.let { return it }
        val entry = ExtensionContentRegistry.resolve(id) ?: return null
        val detail = engine.load(entry.provider, entry.url) ?: return null
        cache[id] = detail
        recordAnilistId(detail)
        return detail
    }

    /**
     * Takes the AniList id straight from the provider, when it offers one.
     *
     * This is the difference between exact tracking and guessing. A provider
     * that reports its AniList id has told us precisely which entry the page IS
     * — no title normalisation, no season ambiguity, no chance of filing
     * episodes into the wrong show. Writing it into the link store here means
     * the player needs no new plumbing: the tracker finds it by the ordinary
     * lookup, exactly as it would find one the user made by hand.
     *
     * `auto = false` on purpose. The flag means "we guessed"; this is not a
     * guess, and the UI must not label it as one.
     *
     * Only ever ADDS a link. A user who deliberately re-pointed a title at a
     * different entry must not have that overwritten by the source on the next
     * page load.
     */
    private fun recordAnilistId(detail: ExtDetail) {
        val mediaId = detail.anilistId ?: return
        if (mediaId <= 0) return

        sourceRegistry.remember(detail.provider)
        if (links.mediaIdFor(detail.provider, detail.contentUrl) != null) return

        links.save(
            AnilistTitleLink(
                provider = detail.provider,
                contentId = detail.contentUrl,
                mediaId = mediaId,
                title = detail.title,
                coverImage = detail.thumbnail,
                totalEpisodes = detail.episodes.size.takeIf { it > 0 },
                linkedAt = System.currentTimeMillis(),
                auto = false,
            )
        )
    }

    private suspend fun detailResult(id: Int): Result<DetailModel> {
        return try {
            val detail = loadDetail(id)
                ?: return Result.failure(IllegalStateException("Content not found for this source."))
            Result.success(detail.toDetailModel(id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadAnimeDetail(id: Int): Result<DetailModel> = detailResult(id)
    override suspend fun loadMovieDetail(id: Int): Result<DetailModel> = detailResult(id)
    override suspend fun loadSeriesDetail(id: Int): Result<DetailModel> = detailResult(id)

    override suspend fun loadRandomAnime(): Result<List<MainModel>> = Result.success(emptyList())

    override suspend fun loadCast(id: Int): Result<List<Cast>> {
        return try {
            val detail = loadDetail(id)
            Result.success(detail?.cast?.mapIndexed { i, c -> c.toCast(i) } ?: emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadCastMovieSeries(id: Int, isMovie: Boolean): Result<List<Cast>> =
        loadCast(id)

    override suspend fun loadAnimeRelations(id: Int): Result<List<MainModel>> {
        return try {
            val detail = loadDetail(id)
            Result.success(detail?.related?.map { it.toMainModel() } ?: emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadMovieOrSeriesRelations(id: Int, isMovie: Boolean): Result<List<MainModel>> =
        loadAnimeRelations(id)

    override suspend fun characterDetail(id: Int): Result<CastDetailModel> =
        Result.failure(UnsupportedOperationException("Character details are not available from extensions."))

    override suspend fun creditDetail(id: Int): Result<CastDetailModel> =
        Result.failure(UnsupportedOperationException("Credit details are not available from extensions."))
}
