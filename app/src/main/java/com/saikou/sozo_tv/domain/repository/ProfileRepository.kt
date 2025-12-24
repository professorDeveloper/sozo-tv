package com.saikou.sozo_tv.domain.repository

import com.saikou.sozo_tv.data.model.anilist.Profile

interface ProfileRepository {
    suspend fun getCurrentProfileId(): Result<Profile>
}