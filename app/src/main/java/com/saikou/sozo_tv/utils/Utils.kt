package com.saikou.sozo_tv.utils

import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object Utils {

    var httpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient()

    fun get(
        url: String,
        mapOfHeaders: Map<String, String>? = null
    ): String {
        val requestBuilder = Request.Builder().url(url)
        if (!mapOfHeaders.isNullOrEmpty()) {
            mapOfHeaders.forEach {
                requestBuilder.addHeader(it.key, it.value)
            }
        }
        return httpClient.newCall(requestBuilder.build())
            .execute().body.string()
    }

    fun post(
        url: String,
        mapOfHeaders: Map<String, String>? = null,
        payload: Map<String, String>? = null
    ): String {
        val requestBuilder = Request.Builder().url(url)

        if (!mapOfHeaders.isNullOrEmpty()) {
            mapOfHeaders.forEach {
                requestBuilder.addHeader(it.key, it.value)
            }
        }

        val requestBody = payload?.let {
            FormBody.Builder().apply {
                it.forEach { (key, value) ->
                    add(key, value)
                }
            }.build()
        }

        if (requestBody != null) {
            requestBuilder.post(requestBody)
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        return response.body.string().toString()
    }

    fun getJsoup(
        url: String,
        mapOfHeaders: Map<String, String>? = null
    ): Document {
        return if (url.startsWith("http") || url.startsWith("https")) {
            Jsoup.parse(get(url, mapOfHeaders))
        } else {
            Jsoup.parse(get("https://$url", mapOfHeaders))
        }
    }

}