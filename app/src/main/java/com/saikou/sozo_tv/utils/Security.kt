package com.saikou.sozo_tv.utils

object Security {
    init {
        System.loadLibrary("native-lib")
    }

    private external fun getApiKey(): String

    fun getToken(): String {
        return "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiI5ZTdkNmQ1MWJkNDk3Y2IwYjAwYmExZTg3NThkZDI5NyIsIm5iZiI6MTc2MTM4MDUwMC4yNDgsInN1YiI6IjY4ZmM4ODk0ZGNhYTQ1NWNmMDlmYTdkMyIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.NvIFHc4QnhjROWs3jWWbRHWkibrBa_vxalXOYcbJhtQ"
    }
}
