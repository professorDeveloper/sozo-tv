package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.extensions.toMainModel
import com.saikou.sozo_tv.domain.model.CategoryChip
import com.saikou.sozo_tv.domain.model.SearchResults
import com.saikou.sozo_tv.domain.repository.CategoriesRepository

/**
 * Genre/category browsing driven by the ACTIVE provider's real catalog. Chips come from
 * engine.genres() (Cloud/Hybrid REST sources), falling back to the provider's home()
 * sections (JS-catalog sources that expose no genres). Each chip carries a section slug;
 * selecting it pages engine.section(slug). Providers exposing neither genres nor sections
 * return an empty chip list, and content falls back to the legacy title-search on the genre
 * name (the AniList / hardcoded LocalData.genres path), preserving the default AniList source.
 */
class CategoriesRepositoryImpl(
    private val engine: ExtensionEngine,
) : CategoriesRepository {

    private suspend fun byGenre(sr: SearchResults): Result<SearchResults> {
        return try {
            val slug = sr.slug
            if (!slug.isNullOrEmpty()) {
                // Provider-driven: page the real catalog section/genre (proven via View-All).
                val page = engine.section(provider = null, slug = slug, page = sr.currentPage)
                    ?: return Result.failure(IllegalStateException("No source selected."))
                sr.results = page.items.map { it.toMainModel() }
                sr.currentPage = page.page
                sr.hasNextPage = page.page < page.totalPages
                return Result.success(sr)
            }
            // Fallback (AniList / hardcoded genres): title text-search on the genre name.
            val query = sr.genre?.takeIf { it.isNotBlank() } ?: sr.tag
            val page = engine.search(null, query)
                ?: return Result.failure(IllegalStateException("No source selected."))
            sr.results = page.items.map { it.toMainModel() }
            sr.hasNextPage = false
            sr.currentPage = 1
            Result.success(sr)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadGenres(): Result<List<CategoryChip>> {
        return try {
            // 1. Real genre chips from the provider (Cloud/Hybrid REST catalogs).
            val genres = engine.genres()
                .filter { it.name.isNotBlank() }
                .map { CategoryChip(it.name, it.slug) }
            if (genres.isNotEmpty()) return Result.success(genres)
            // 2. JS-catalog providers expose no genres() -> use home() sections, whose slug
            //    is already the "type::slug" form engine.section() expects.
            val sections = engine.home()?.sections.orEmpty()
                .filter { it.label.isNotBlank() && !it.slug.isNullOrEmpty() }
                .map { CategoryChip(it.label, it.slug) }
            if (sections.isNotEmpty()) return Result.success(sections)
            // 3. No provider catalog -> signal the screen to use its local fallback list.
            Result.success(emptyList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadAnimeByGenre(searchResults: SearchResults): Result<SearchResults> =
        byGenre(searchResults)

    override suspend fun loadMovieByGenre(searchResults: SearchResults): Result<SearchResults> =
        byGenre(searchResults)
}
