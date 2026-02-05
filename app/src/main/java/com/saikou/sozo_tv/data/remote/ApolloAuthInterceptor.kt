package com.saikou.sozo_tv.data.remote

import com.saikou.sozo_tv.data.local.pref.AuthPrefKeys
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import okhttp3.Interceptor
import okhttp3.Response

class ApolloAuthInterceptor(
    private val prefs: PreferenceManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()

        if (req.url.host != "graphql.anilist.co") {
            return chain.proceed(req)
        }

        val token = prefs.getString(AuthPrefKeys.ANILIST_TOKEN)
        if (token.isBlank()) {
            return chain.proceed(req)
        }

        val newReq = req.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(newReq)
    }
}
