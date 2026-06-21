package com.saikou.sozo_tv.data.remote.anilist

/** AniList GraphQL documents. `${'$'}` keeps the `$` literal inside Kotlin string templates. */
object AniListQueries {

    /** Catalog search. `mediaListEntry` is non-null only when an Authorization token is sent. */
    val SEARCH_ANIME = """
        query (${'$'}search: String, ${'$'}page: Int) {
          Page(page: ${'$'}page, perPage: 30) {
            media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH, isAdult: false) {
              id
              title { romaji english native }
              coverImage { large extraLarge medium }
              averageScore
              format
              episodes
              genres
              status
              mediaListEntry { status progress score }
            }
          }
        }
    """.trimIndent()

    /** Add/update the media on the signed-in user's list. Requires a Bearer token. */
    val SAVE_ENTRY = """
        mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}progress: Int) {
          SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status, progress: ${'$'}progress) {
            id
            status
            progress
          }
        }
    """.trimIndent()
}
