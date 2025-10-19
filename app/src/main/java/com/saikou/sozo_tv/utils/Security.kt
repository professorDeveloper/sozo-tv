package com.saikou.sozo_tv.utils

object Security {
    init {
        System.loadLibrary("native-lib")
    }

    // Native function — C++ dan to'g'ridan-to'g'ri tokenni oladi
    private external fun getApiKey(): String

    // Public wrapper, kerak bo'lsa qo'shimcha logika qo'shish mumkin
    fun getToken(): String {
        return getApiKey()
    }
}
