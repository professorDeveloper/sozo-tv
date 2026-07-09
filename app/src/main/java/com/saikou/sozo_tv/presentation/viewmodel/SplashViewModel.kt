package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.domain.model.AppUpdate
import com.saikou.sozo_tv.services.FirebaseService
import com.saikou.sozo_tv.utils.AppUtils
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SplashViewModel(
    private val firebaseService: FirebaseService
) : ViewModel() {

    private val _initSplash = MutableLiveData<Resource<Unit>>(Resource.Idle)
    val initSplash = _initSplash

    val isUpdateAvailableLiveData = MutableLiveData<Boolean>()
    val getAppUpdateInfo = MutableLiveData<AppUpdate>()

    init {
        checkForAppUpdate()
    }

    /**
     * The splash cannot leave until this answers, so it must always answer: cap the wait and
     * treat "no answer" as "no update". [getAppUpdateInfo] is published before the flag so an
     * observer reacting to `true` always finds the payload already there.
     */
    private fun checkForAppUpdate() {
        viewModelScope.launch {
            val update = withTimeoutOrNull(UPDATE_CHECK_TIMEOUT_MS) {
                firebaseService.fetchAppUpdate()
            }
            val isUpdateAvailable = update != null &&
                    update.versionCode > AppUtils.getAppVersionCode(MyApp.context)

            if (isUpdateAvailable) getAppUpdateInfo.value = update
            isUpdateAvailableLiveData.value = isUpdateAvailable
        }
    }

    fun checkSubscribe() {
        _initSplash.value = Resource.Success(Unit)
    }

    private companion object {
        const val UPDATE_CHECK_TIMEOUT_MS = 6_000L
    }
}
