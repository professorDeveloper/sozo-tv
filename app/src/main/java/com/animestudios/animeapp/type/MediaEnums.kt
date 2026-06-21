package com.animestudios.animeapp.type

/**
 * Hand-written replacements for the former Apollo/AniList generated enums.
 *
 * The app was migrated off AniList GraphQL onto the Aniyomi/CloudStream extension
 * engine. A handful of UI models ([HomeModel], [DetailModel]) and adapters still
 * reference these enum types by name (`.name`, `MediaFormat.MOVIE`, …), so we keep
 * source-compatible enums here instead of touching every call site.
 */
enum class MediaFormat {
    TV,
    TV_SHORT,
    MOVIE,
    SPECIAL,
    OVA,
    ONA,
    MUSIC,
    MANGA,
    NOVEL,
    ONE_SHOT,
    UNKNOWN__;
}

enum class MediaSource {
    ORIGINAL,
    MANGA,
    LIGHT_NOVEL,
    VISUAL_NOVEL,
    VIDEO_GAME,
    OTHER,
    NOVEL,
    DOUJINSHI,
    ANIME,
    WEB_NOVEL,
    LIVE_ACTION,
    GAME,
    COMIC,
    MULTIMEDIA_PROJECT,
    PICTURE_BOOK,
    UNKNOWN__;
}

enum class MediaSort {
    START_DATE,
    START_DATE_DESC,
    STATUS,
    STATUS_DESC,
    TITLE_ENGLISH,
    TITLE_ENGLISH_DESC,
    TITLE_NATIVE,
    TITLE_NATIVE_DESC,
    TITLE_ROMAJI,
    TITLE_ROMAJI_DESC,
    TRENDING,
    TRENDING_DESC,
    TYPE,
    TYPE_DESC,
    UPDATED_AT,
    UPDATED_AT_DESC,
    VOLUMES,
    VOLUMES_DESC,
    POPULARITY,
    POPULARITY_DESC,
    SCORE,
    SCORE_DESC,
    UNKNOWN__;
}
