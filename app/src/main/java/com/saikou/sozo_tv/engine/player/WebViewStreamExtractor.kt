package com.saikou.sozo_tv.engine.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.saikou.sozo_tv.utils.SOZO_USER_AGENT
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URI
import kotlin.coroutines.resume

data class ExtractedStream(val url: String, val headers: Map<String, String>, val playType: String)

/** Capture before sending the media request: signed manifests can be single use. */
object WebViewStreamExtractor {
    private val patterns = listOf(".m3u8", ".mpd", ".mp4")
    fun needsExtraction(url: String): Boolean {
        val path = runCatching { URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        if (listOf(".m3u8", ".mpd", ".mp4", ".mkv", ".webm").any { path.endsWith(it) }) return false
        return listOf("/embed/", "/player/", "/e/").any { path.startsWith(it) } ||
            listOf(".html", ".htm", ".php", "/").any { path.endsWith(it) }
    }

    private class CaptureBridge(private val receive: (String) -> Unit) {
        @JavascriptInterface fun capture(payload: String) = receive(payload)
    }

    suspend fun extract(context: Context, pageUrl: String, pageHeaders: Map<String, String> = emptyMap(),
        timeoutMs: Long = 20_000L, patterns: List<String> = this.patterns,
        blockHosts: List<String> = emptyList()): ExtractedStream? = suspendCancellableCoroutine { cont ->
        val main = Handler(Looper.getMainLooper())
        var view: WebView? = null
        var found: ExtractedStream? = null
        var bestRank = Int.MAX_VALUE
        var finished = false
        val pats = patterns.ifEmpty { this.patterns }.map { it.lowercase() }
        val blocked = blockHosts + listOf("doubleclick.net", "google-analytics.com", "googlesyndication.com")
        fun finish(result: ExtractedStream?) {
            if (finished) return
            finished = true
            main.removeCallbacksAndMessages(null)
            view?.let { it.stopLoading(); it.removeJavascriptInterface("SozoCapture"); it.destroy() }
            view = null
            if (cont.isActive) cont.resume(result)
        }
        fun rank(url: String) = pats.indexOfFirst { url.substringBefore('?').lowercase().contains(it) }
        fun capture(url: String, headers: Map<String, String>, frame: String) {
            main.post {
                if (finished || !cont.isActive) return@post
                val uri = runCatching { URI(url) }.getOrNull() ?: return@post
                if (uri.scheme !in setOf("https", "http") || uri.host == null) return@post
                val r = rank(url)
                if (r < 0 || r >= bestRank) return@post
                val first = found == null
                val merged = runCatching {
                    StreamRequestRules.headers(pageHeaders, headers, frame, SOZO_USER_AGENT,
                        CookieManager.getInstance().getCookie(url))
                }.getOrNull() ?: return@post
                found = ExtractedStream(url, merged, StreamRequestRules.playType(url))
                bestRank = r
                if (r == 0) finish(found)
                else if (first) main.postDelayed({ finish(found) }, 2500)
            }
        }
        main.post {
            if (!cont.isActive) return@post
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = pageHeaders.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: SOZO_USER_AGENT
                // Keep a viewport for player overlays that require a center click.
                layout(0, 0, 1280, 720)
            }
            view = wv
            wv.addJavascriptInterface(CaptureBridge { raw ->
                runCatching {
                    val obj = JSONObject(raw)
                    val h = obj.optJSONObject("headers") ?: JSONObject()
                    capture(obj.getString("url"), h.keys().asSequence().associateWith { h.getString(it) }, obj.optString("frame", pageUrl))
                }
            }, "SozoCapture")
            val script = captureScript(pats)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(wv, script, setOf("*"))
            }
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String) { v.evaluateJavascript(script, null) }
                override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    val host = request.url.host.orEmpty().lowercase()
                    if (blocked.any { host == it || host.endsWith(".$it") }) return empty()
                    if (rank(url) < 0) return null
                    val h = request.requestHeaders.orEmpty()
                    capture(url, h, h.entries.firstOrNull { it.key.equals("Referer", true) }?.value ?: pageUrl)
                    return empty()
                }
            }
            main.postDelayed({ finish(found) }, timeoutMs)
            wv.loadUrl(pageUrl, pageHeaders)
        }
        cont.invokeOnCancellation { main.post { finish(null) } }
    }

    private fun empty() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(byteArrayOf()))

    internal fun captureScript(patterns: List<String>): String = """
    (function(){
      if(window.__sozoCaptureInstalled)return; window.__sozoCaptureInstalled=true;
      var pats=${JSONArray(patterns)};
      function report(u,h){try{
        u=new URL(u,location.href).href;
        if(!pats.some(function(p){return u.split('?')[0].toLowerCase().indexOf(p)>=0;}))return false;
        var headers=Object.assign({Referer:location.href,Origin:location.origin},h||{});
        SozoCapture.capture(JSON.stringify({url:u,headers:headers,frame:location.href}));return true;
      }catch(e){return false;}}
      var open=XMLHttpRequest.prototype.open,set=XMLHttpRequest.prototype.setRequestHeader,send=XMLHttpRequest.prototype.send;
      XMLHttpRequest.prototype.open=function(m,u){this.__sozoUrl=u;this.__sozoHeaders={};return open.apply(this,arguments);};
      XMLHttpRequest.prototype.setRequestHeader=function(k,v){this.__sozoHeaders[k]=String(v);return set.apply(this,arguments);};
      XMLHttpRequest.prototype.send=function(){if(report(this.__sozoUrl,this.__sozoHeaders))return;return send.apply(this,arguments);};
      var fetch=window.fetch;
      if(fetch)window.fetch=function(input,init){var h={};try{new Headers((init&&init.headers)||(input&&input.headers)||{}).forEach(function(v,k){h[k]=v;});}catch(e){}
        if(report(typeof input==='string'?input:input.url,h))return new Promise(function(){});return fetch.apply(this,arguments);};
      window.open=function(){return null;};
      var tries=0,t=setInterval(function(){if(++tries>12){clearInterval(t);return;}
        try{var v=document.querySelector('video');if(v){v.muted=true;var p=v.play();if(p)p.catch(function(){});}
          var e=document.querySelector('.vjs-big-play-button,.jw-icon-playback,[aria-label="Play"],.plyr__control--overlaid')||document.elementFromPoint(innerWidth/2,innerHeight/2);
          if(e&&e.tagName!=='A'&&e.tagName!=='IFRAME')e.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));
        }catch(e){}},1000);
    })();
    """.trimIndent()
}
