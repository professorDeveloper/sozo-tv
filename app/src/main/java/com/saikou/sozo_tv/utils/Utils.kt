package com.saikou.sozo_tv.utils

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object Utils {

     var httpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient()

    fun getAsilMedia(
        host: String? = null,
        pathSegment: ArrayList<String>? = null,
        mapOfHeaders: Map<String, String>? = null,
        params: Map<String, String>? = null,
    ): String {
        val urlBuilder = HttpUrl.Builder()
            .scheme("http")
            .host(host!!)
        pathSegment?.forEach {
            urlBuilder.addPathSegment(it)
        }


        if (!params.isNullOrEmpty()) {
            params.forEach {
                urlBuilder.addQueryParameter(it.key, it.value)
            }
        }

        val requestBuilder = Request.Builder().url(urlBuilder.build())
        if (!mapOfHeaders.isNullOrEmpty()) {
            mapOfHeaders.forEach {
                requestBuilder.addHeader(it.key, it.value)
            }
        }
        val data = httpClient.newCall(requestBuilder.build())
            .execute()

        println(data.body?.string())
        return data.body.string()
    }

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
            .execute().body!!.string()
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
        return response.body?.string().toString()
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