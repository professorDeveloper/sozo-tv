package com.saikou.sozo_tv.service

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.saikou.sozo_tv.app.MyApp
import kotlinx.coroutines.delay

class DDoSGuardSolver {
    fun solveChallenge(url: String, callback: (String) -> Unit) {
        val webView = WebView(MyApp.context)
        webView.settings.javaScriptEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val cookies = CookieManager.getInstance().getCookie(url)
                    callback(cookies)
                }, 5000)
            }
        }

        webView.loadUrl(url)
    }
}


// Extension function to add retry capability to any request
suspend fun <T> retryRequest(
    maxRetries: Int = 3,
    baseDelay: Long = 1000L,
    block: suspend () -> T
): T {
    var lastException: Exception? = null

    repeat(maxRetries) { attempt ->
        try {
            if (attempt > 0) {
                delay(baseDelay * (attempt + 1))
            }
            return block()
        } catch (e: Exception) {
            lastException = e
            if (attempt == maxRetries - 1) {
                throw e
            }
        }
    }

    throw lastException ?: Exception("Max retries exceeded")
}

// Helper function to validate JSON response
fun isValidJsonResponse(response: String): Boolean {
    return response.trim().let {
        it.startsWith("{") || it.startsWith("[")
                && !it.contains("<!doctype html>", ignoreCase = true)
    }
}