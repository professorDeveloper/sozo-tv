package com.saikou.sozo_tv.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.domain.model.AppUpdate
import com.saikou.sozo_tv.services.FirebaseService
import com.saikou.sozo_tv.utils.AppUtils
import com.saikou.sozo_tv.utils.Resource

class SplashViewModel(
    private val firebaseService: FirebaseService
) : ViewModel() {
    private val _initSplash = MutableLiveData<Resource<Unit>>(Resource.Idle)
    val initSplash = _initSplash
    val isUpdateAvailableLiveData: MutableLiveData<Boolean> = MutableLiveData()
    val getAppUpdateInfo: MutableLiveData<AppUpdate> = MutableLiveData()

    init {
        checkForAppUpdate()
    }

    private fun checkForAppUpdate() {
        firebaseService.getAppUpdateInfo().observeForever { appUpdate ->
            if (appUpdate != null) {
                if (isUpdateAvailable(
                        AppUtils.getAppVersion(context = MyApp.context)!!,
                        appUpdate.version
                    )
                ) {

                    isUpdateAvailableLiveData.postValue(true)
                } else {
                    isUpdateAvailableLiveData.postValue(false)
                }
            } else {
                isUpdateAvailableLiveData.postValue(false)
            }
        }
    }

    fun getAppUpdateInfo() {
        firebaseService.getAppUpdateInfo().observeForever { appUpdate ->
            appUpdate?.let {
                getAppUpdateInfo.postValue(appUpdate!!)
            }
        }
    }

    private fun isUpdateAvailable(currentVersion: String, newVersion: String?): Boolean {
        return if (newVersion != null) {
            compareVersions(currentVersion, newVersion) < 0
        } else {
            false
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val version1Parts = version1.split(".").map { it.toInt() }
        val version2Parts = version2.split(".").map { it.toInt() }

        for (i in 0 until Math.min(version1Parts.size, version2Parts.size)) {
            val comparison = version1Parts[i].compareTo(version2Parts[i])
            if (comparison != 0) {
                return comparison
            }
        }
        Log.d("GGG", "compareVersions:${version1Parts} ${version2Parts} ")
        return version1Parts.size.compareTo(version2Parts.size)
    }

    fun checkSubscribe() {
        _initSplash.value = Resource.Success(Unit)
    }
}