package com.saikou.sozo_tv.data.model.tmdb

import com.saikou.sozo_tv.utils.LocalData

data class MediaDetails(
    val id: Int,
    val adult: Boolean,
    val backdrop_path: String?,
    val genres: List<Genre>?,
    val homepage: String?,
    val original_language: String?,
    val overview: String?,
    val popularity: Double?,
    val poster_path: String?,
    val production_companies: List<ProductionCompany>?,
    val production_countries: List<ProductionCountry>?,
    val spoken_languages: List<SpokenLanguage>?,
    val status: String?,
    val tagline: String?,
    val vote_average: Double?,
    val vote_count: Int?,

    // Movie-specific
    val title: String?,                // Movie title
    val original_title: String?,
    val release_date: String?,
    val budget: Long?,
    val revenue: Long?,
    val runtime: Int?,
    val video: Boolean?,

    // TV-specific
    val name: String?,                 // Series name
    val original_name: String?,
    val first_air_date: String?,
    val last_air_date: String?,
    val number_of_episodes: Int?,
    val number_of_seasons: Int?,
    val in_production: Boolean?,
    val languages: List<String>?,
    val episode_run_time: List<Int>?,
    val created_by: List<CreatedBy>?,
    val networks: List<Network>?,
    val last_episode_to_air: LastEpisodeToAir?,
    val seasons: List<Season>?,
    val type: String?
) {
    val titleFormat: String?
        get() = name ?: original_name ?: title
    val imageUrl: String?
        get() = poster_path?.let { LocalData.IMDB_IMAGE_PATH + it }

}
data class Genre(
    val id: Int,
    val name: String
)

data class ProductionCompany(
    val id: Int,
    val logo_path: String?,
    val name: String?,
    val origin_country: String?
)

data class ProductionCountry(
    val iso_3166_1: String?,
    val name: String?
)

data class SpokenLanguage(
    val english_name: String?,
    val iso_639_1: String?,
    val name: String?
)

data class CreatedBy(
    val id: Int,
    val credit_id: String?,
    val name: String?,
    val gender: Int?,
    val profile_path: String?
)

data class Network(
    val id: Int,
    val logo_path: String?,
    val name: String?,
    val origin_country: String?
)

data class LastEpisodeToAir(
    val id: Int,
    val name: String?,
    val overview: String?,
    val vote_average: Double?,
    val vote_count: Int?,
    val air_date: String?,
    val episode_number: Int?,
    val production_code: String?,
    val runtime: Int?,
    val season_number: Int?,
    val show_id: Int?,
    val still_path: String?
)

data class Season(
    val air_date: String?,
    val episode_count: Int,
    val id: Int,
    val name: String?,
    val overview: String?,
    val poster_path: String?,
    val season_number: Int,
    val vote_average: Double?
)
