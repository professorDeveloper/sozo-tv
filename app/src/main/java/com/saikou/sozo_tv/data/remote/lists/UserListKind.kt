package com.saikou.sozo_tv.data.remote.lists

/**
 * The user-curated lists the backend exposes at `/auth/lists/<slug>`.
 *
 * [slug] MUST match the server's registry (`authService.USER_LISTS`) and the
 * Flutter enum of the same name — one list, three places, one spelling.
 */
enum class UserListKind(val slug: String, val label: String) {
    WATCH_LATER("watch-later", "Watch Later"),
    WATCHED("watched", "Watched"),
}
