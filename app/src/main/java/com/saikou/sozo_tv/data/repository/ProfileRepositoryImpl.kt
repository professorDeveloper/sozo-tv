package com.saikou.sozo_tv.data.repository

import com.saikou.sozo_tv.data.local.pref.DeviceSessionStore
import com.saikou.sozo_tv.data.model.anilist.Profile
import com.saikou.sozo_tv.domain.repository.ProfileRepository

/**
 * Reads the profile straight out of the stored device session — the approved pairing response
 * already carries the full user object, so a profile round-trip per screen would be redundant.
 * Falls back to a guest placeholder when the TV is not linked.
 */
class ProfileRepositoryImpl(private val store: DeviceSessionStore) : ProfileRepository {

    override suspend fun getCurrentProfileId(): Result<Profile> {
        val session = store.current() ?: return Result.success(GUEST)
        return Result.success(
            Profile(
                id = -1,
                name = session.displayName ?: session.username,
                avatarUrl = session.photoUrl,
                bannerImg = "",
                unreadNotificationCount = 0,
                email = session.email,
            )
        )
    }

    private companion object {
        val GUEST = Profile(
            id = -1,
            name = "Guest",
            avatarUrl = "",
            bannerImg = "",
            unreadNotificationCount = 0,
        )
    }
}
