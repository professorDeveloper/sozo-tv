package com.saikou.sozo_tv.utils

object Security {
    init {
        System.loadLibrary("native-lib")
    }

    private external fun getApiKey(): String

    fun getToken(): String {
        return "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIzZjVlYWQ5OTIzNmRhNGU1YzUyNjA1NTJjYzI5NTQzYyIsIm5iZiI6MTY0OTcwMTIwMy44ODk5OTk5LCJzdWIiOiI2MjU0NzE1MzY3ZTBmNzM5YzFhMjIyMzQiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.s6vYAqE1eUVxpVRVRoG6BGwiq8BuI9mf0tBHCiCDl7s"
    }
}
