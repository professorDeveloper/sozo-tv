package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.domain.model.AppUpdate
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.data.remote.version.AppVersionCheck
import com.saikou.sozo_tv.data.remote.version.AppVersionClient
import com.saikou.sozo_tv.utils.AppUtils
import com.saikou.sozo_tv.utils.Resource
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SplashViewModel(
    private val appVersionClient: AppVersionClient,
) : ViewModel() {

    private val _initSplash = MutableLiveData<Resource<Unit>>(Resource.Idle)
    val initSplash = _initSplash

    val isUpdateAvailableLiveData = MutableLiveData<Boolean>()
    val getAppUpdateInfo = MutableLiveData<AppUpdate>()

    init {
        checkForAppUpdate()
    }

    /**
     * Asks the backend whether a newer TV build exists.
     *
     * Reads the same `/app-version` endpoint the phone reads, so the admin panel
     * governs both apps. It previously read a Firebase Realtime Database node
     * that nothing else wrote to, which meant shipping a TV update required
     * editing Firebase by hand while every other release went through the admin
     * screen — two sources of truth for the same question.
     *
     * The splash cannot leave until this answers, so it must ALWAYS answer: the
     * wait is capped and "no answer" is treated as "no update".
     * [getAppUpdateInfo] is published before the flag so an observer reacting to
     * `true` always finds the payload already there.
     */
    private fun checkForAppUpdate() {
        viewModelScope.launch {
            val current = AppUtils.getAppVersionCode(MyApp.context)
            val check = withTimeoutOrNull(UPDATE_CHECK_TIMEOUT_MS) {
                (appVersionClient.check(current) as? ApiResult.Ok)?.body
            }

            // The server already compared versions against the row it holds.
            // Re-checking `version > current` here anyway costs nothing and
            // means a stale or misconfigured row cannot make the box offer an
            // update to a build it is already running — which would loop.
            val update = check
                ?.takeIf { it.isActionable && (it.version ?: 0L) > current }
                ?.toAppUpdate()

            if (update != null) getAppUpdateInfo.value = update
            isUpdateAvailableLiveData.value = update != null
        }
    }

    private fun AppVersionCheck.toAppUpdate() = AppUpdate(
        versionCode = version ?: 0L,
        // forceUpdate is only true when the server has ALSO decided this build
        // is below minVersion, so it is safe to pass straight through.
        isMandatory = forceUpdate == true,
        changeLog = releaseNotes,
        appLink = installUrl,
    )

    fun checkSubscribe() {
        _initSplash.value = Resource.Success(Unit)
    }

    private companion object {
        const val UPDATE_CHECK_TIMEOUT_MS = 6_000L
    }
}
