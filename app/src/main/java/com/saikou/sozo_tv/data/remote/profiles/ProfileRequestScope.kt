package com.saikou.sozo_tv.data.remote.profiles

import okhttp3.Interceptor
import okhttp3.Response

/** An explicit null means the captured legacy/default profile, never the current profile. */
data class ProfileRequestScope(val id: String?)

class ProfileHeaderInterceptor(private val activeProfile: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val scope = request.tag(ProfileRequestScope::class.java)
        val id = if (scope != null) scope.id else activeProfile()
        val next = request.newBuilder().removeHeader("X-Sozo-Profile").apply {
            if (!id.isNullOrBlank()) header("X-Sozo-Profile", id)
        }.build()
        return chain.proceed(next)
    }
}
