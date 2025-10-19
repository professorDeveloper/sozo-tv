package com.saikou.sozo_tv.utils

object Security {
    init {
        System.loadLibrary("native-lib")
    }

    private external fun getApiKey(): String

    fun getToken(): String {
        return getApiKey()
    }
}
