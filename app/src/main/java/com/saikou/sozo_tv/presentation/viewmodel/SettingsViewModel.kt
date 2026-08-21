package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bugsnag.android.Bugsnag
import com.saikou.sozo_tv.data.local.pref.DeviceSession
import com.saikou.sozo_tv.data.model.ContentMode
import com.saikou.sozo_tv.data.model.SeasonalTheme
import com.saikou.sozo_tv.data.model.anilist.Profile
import com.saikou.sozo_tv.data.repository.AnilistConnection
import com.saikou.sozo_tv.data.repository.AnilistRepository
import com.saikou.sozo_tv.data.repository.DeviceAuthRepository
import com.saikou.sozo_tv.data.repository.WatchHistorySyncRepository
import com.saikou.sozo_tv.data.repository.UserListsRepository
import com.saikou.sozo_tv.domain.repository.MovieBookmarkRepository
import com.saikou.sozo_tv.domain.repository.ProfileRepository
import com.saikou.sozo_tv.domain.repository.WatchHistoryRepository
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
    private val history: WatchHistoryRepository,
    private val bookmarks: MovieBookmarkRepository,
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

    /**
     * What the account actually has on this box.
     *
     * Counts only, and deliberately: total watch time would need a duration
     * field on the history row, and inventing a number for a profile screen is
     * worse than not showing one.
     */
    data class AccountStats(val watched: Int, val saved: Int)

    val accountStats = MutableLiveData<AccountStats>()

    fun loadStats() {
        viewModelScope.launch {
            runCatching {
                AccountStats(
                    watched = history.getAllHistory().size,
                    saved = bookmarks.getAllBookmarks().size,
                )
            }.onSuccess { accountStats.postValue(it) }
        }
    }

    /** The linked AniList account, for surfaces that want to show who is connected. */
    val anilistConnection: StateFlow<AnilistConnection> get() = anilist.connection

    /**
     * The link is made on the phone, so the TV can hold a stale "not connected" for as long as
     * it has been running. Any screen that shows the account asks for a re-check.
     */
    fun refreshAnilist() {
        viewModelScope.launch { runCatching { anilist.refresh() } }
    }

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
        userLists.clear()
        historySync.clear()
        anilist.forgetLocal()

        // Runs on the repository's scope, not viewModelScope: the Exit dialog relaunches
        // MainActivity with CLEAR_TASK in the same handler, which tears this ViewModel down and
        // would abandon the revocation call mid-flight, leaving the session alive server-side.
        deviceAuth.logoutAsync()
    }
}
