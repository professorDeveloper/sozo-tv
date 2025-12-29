package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bugsnag.android.Bugsnag
import com.saikou.sozo_tv.data.local.pref.AuthPrefKeys
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.model.ContentMode
import com.saikou.sozo_tv.data.model.SeasonalTheme
import com.saikou.sozo_tv.data.model.anilist.Profile
import com.saikou.sozo_tv.domain.repository.ProfileRepository
import com.saikou.sozo_tv.domain.repository.SettingsRepository
import com.saikou.sozo_tv.utils.LocalData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val profileRepo: ProfileRepository
) : ViewModel() {

    val contentMode: StateFlow<ContentMode> =
        settingsRepository.contentMode
            .stateIn(viewModelScope, SharingStarted.Eagerly, ContentMode.ANIME)

    val seasonalTheme: StateFlow<SeasonalTheme> =
        settingsRepository.seasonalTheme
            .stateIn(viewModelScope, SharingStarted.Eagerly, SeasonalTheme.DEFAULT)


    val profileData = MutableLiveData<Profile>()

    init {
        viewModelScope.launch {
            contentMode.collect { mode ->
                LocalData.isAnimeEnabled = (mode == ContentMode.ANIME)
            }
        }
    }

    fun loadProfile() {
        val preference = PreferenceManager()
        viewModelScope.launch {
            val token = preference.getString(AuthPrefKeys.ANILIST_TOKEN)
            if (token.isNotEmpty()) {
                val result = profileRepo.getCurrentProfileId()
                result.onSuccess { profile ->
                    profileData.postValue(profile)
                }.onFailure {
                    Bugsnag.notify(it)
                }
            }
        }
    }

    fun setContentMode(mode: ContentMode) = settingsRepository.setContentMode(mode)

    fun setSeasonalTheme(theme: SeasonalTheme) = settingsRepository.setSeasonalTheme(theme)
    fun exitUser() {
        val preference = PreferenceManager()
        viewModelScope.launch {
            preference.putString(AuthPrefKeys.ANILIST_TOKEN, "")
        }
    }
}
