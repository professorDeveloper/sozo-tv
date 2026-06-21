package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.model.anilist.Profile
import com.saikou.sozo_tv.domain.repository.ProfileRepository

/** AniList account sign-in was removed with the GraphQL backend; returns a guest. */
class ProfileRepositoryImpl : ProfileRepository {
    override suspend fun getCurrentProfileId(): Result<Profile> =
        Result.success(
            Profile(
                id = -1,
                name = "Guest",
                avatarUrl = "",
                bannerImg = "",
                unreadNotificationCount = 0,
            )
        )
}
