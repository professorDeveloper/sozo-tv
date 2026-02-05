package com.saikou.sozo_tv.data.repository

import android.util.Log
import com.animestudios.animeapp.GetMyInfoQuery
import com.apollographql.apollo3.ApolloClient
import com.saikou.sozo_tv.data.model.anilist.Profile
import com.saikou.sozo_tv.data.remote.safeExecute
import com.saikou.sozo_tv.domain.repository.ProfileRepository

class ProfileRepositoryImpl(private val apollo: ApolloClient) : ProfileRepository {
    override suspend fun getCurrentProfileId(): Result<Profile> {
        return try {
            val data = apollo.safeExecute(GetMyInfoQuery())
            val profileData = data?.Viewer
            val profile = Profile(
                id = profileData?.id ?: -1,
                name = profileData?.name ?: "Unknown",
                avatarUrl = profileData?.avatar?.large ?: "",
                bannerImg = profileData?.bannerImage ?: "",
                unreadNotificationCount = profileData?.unreadNotificationCount ?: 0
            )
            Log.d("GGG", "profileData:${profileData} ")
            Log.d("GGG", "getCurrentProfileId:${profile} ")
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}