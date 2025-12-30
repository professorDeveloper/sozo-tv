package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.domain.model.AppUpdate
import com.saikou.sozo_tv.services.FirebaseService
import com.saikou.sozo_tv.utils.AppUtils
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.BuildConfig

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

    private fun checkForAppUpdate() {
        if (BuildConfig.DEBUG) {
            isUpdateAvailableLiveData.postValue(false)
            return
        }

        firebaseService.getAppUpdateInfo().observeForever { appUpdate ->
            if (appUpdate == null) {
                isUpdateAvailableLiveData.postValue(false)
                return@observeForever
            }

            val currentVersionCode =
                AppUtils.getAppVersionCode(MyApp.context)

            val remoteVersionCode = appUpdate.versionCode

            val isUpdateAvailable = remoteVersionCode > currentVersionCode

            isUpdateAvailableLiveData.postValue(isUpdateAvailable)

            if (isUpdateAvailable) {
                getAppUpdateInfo.postValue(appUpdate)
            }
        }
    }

    fun checkSubscribe() {
        _initSplash.value = Resource.Success(Unit)
    }
}
