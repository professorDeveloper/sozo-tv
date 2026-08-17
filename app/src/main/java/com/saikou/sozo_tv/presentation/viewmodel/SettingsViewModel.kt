package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bugsnag.android.Bugsnag
import com.saikou.sozo_tv.data.local.pref.DeviceSession
import com.saikou.sozo_tv.data.model.ContentMode
import com.saikou.sozo_tv.data.model.SeasonalTheme
import com.saikou.sozo_tv.data.model.anilist.Profile
import com.saikou.sozo_tv.data.repository.AnilistRepository
import com.saikou.sozo_tv.data.repository.DeviceAuthRepository
import com.saikou.sozo_tv.data.repository.WatchHistorySyncRepository
import com.saikou.sozo_tv.data.repository.UserListsRepository
import com.saikou.sozo_tv.domain.repository.ProfileRepository
import com.saikou.sozo_tv.domain.repository.SettingsRepository
import com.saikou.sozo_tv.utils.LocalData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val profileRepo: ProfileRepository,
    private val deviceAuth: DeviceAuthRepository,
    private val userLists: UserListsRepository,
    private val historySync: WatchHistorySyncRepository,
    private val anilist: AnilistRepository,
) : ViewModel() {

    val contentMode: StateFlow<ContentMode> =
        settingsRepository.contentMode
            .stateIn(viewModelScope, SharingStarted.Eagerly, ContentMode.ANIME)

    val seasonalTheme: StateFlow<SeasonalTheme> =
        settingsRepository.seasonalTheme
            .stateIn(viewModelScope, SharingStarted.Eagerly, SeasonalTheme.DEFAULT)

    /** Lets login-aware surfaces flip the moment the TV links or unlinks, without a rebuild. */
    val deviceSession: StateFlow<DeviceSession?> get() = deviceAuth.session

    val profileData = MutableLiveData<Profile>()

    init {
        viewModelScope.launch {
            contentMode.collect { mode ->
                LocalData.isAnimeEnabled = (mode == ContentMode.ANIME)
            }
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            val result = profileRepo.getCurrentProfileId()
            result.onSuccess { profile ->
                profileData.postValue(profile)
            }.onFailure {
                Bugsnag.notify(it)
            }
        }
    }

    fun setContentMode(mode: ContentMode) = settingsRepository.setContentMode(mode)

    fun setSeasonalTheme(theme: SeasonalTheme) = settingsRepository.setSeasonalTheme(theme)

    /** Revokes only THIS device's session — the user's phone and other TVs stay signed in. */
    fun exitUser() {
        // Account-scoped caches go first, and synchronously. A TV is a shared
        // screen: leaving the previous account's Watch Later and watch history
        // on the box after a visible sign-out shows one person's viewing to the
        // next. Both are plain local wipes, so there is nothing to await.
        userLists.clear()
        historySync.clear()
        // The AniList token belongs to the signed-out account and writes to a
        // real third-party list. Left behind, the next person's viewing would be
        // filed under a stranger's AniList profile. `forgetLocal` drops this
        // box's copy only — it does not unlink the account itself.
        anilist.forgetLocal()

        // Runs on the repository's scope, not viewModelScope: the Exit dialog relaunches
        // MainActivity with CLEAR_TASK in the same handler, which tears this ViewModel down and
        // would abandon the revocation call mid-flight, leaving the session alive server-side.
        deviceAuth.logoutAsync()
    }
}
