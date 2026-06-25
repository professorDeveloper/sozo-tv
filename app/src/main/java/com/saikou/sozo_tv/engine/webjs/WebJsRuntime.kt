package com.saikou.sozo_tv.engine.webjs

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.saikou.sozo_tv.engine.server.ApisozoClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class WebJsRuntime(
    private val context: Context,
    private val client: ApisozoClient = ApisozoClient(),
) {
    private var webView: WebView? = null
    private var ready = false
    private var runtimeLoaded = false
    private var activeExtractor: String? = null
    private var activeVersion = -1

    private val codeCache = ConcurrentHashMap<String, String>()
    private val results = ConcurrentHashMap<String, CompletableDeferred<JsResult>>()
    private val ids = AtomicLong(0)
    private val nativeFetch = NativeFetch()

    private data class JsResult(val value: String?, val error: String?)

    suspend fun callProvider(name: String, version: Int, fn: String, args: JSONArray): String? {
        ensureReady()
        ensureRuntime()
        ensureExtractor(name, version)
        val id = ids.incrementAndGet().toString()
        val deferred = CompletableDeferred<JsResult>()
        results[id] = deferred
        evalOnMain("window.__callProvider(${q(id)}, ${q(fn)}, ${q(args.toString())});")
        val result = withTimeoutOrNull(45000) { deferred.await() }
        results.remove(id)
        if (result == null) return null
        result.error?.let { throw RuntimeException(it) }
        val v = result.value
        return if (v.isNullOrEmpty() || v == "null") null else v
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "AddJavascriptInterface")
    private suspend fun ensureReady() = withContext(Dispatchers.Main) {
        if (ready) return@withContext
        val loaded = CompletableDeferred<Unit>()
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.addJavascriptInterface(FetchBridge(), "AndroidFetch")
        wv.addJavascriptInterface(ResultBridge(), "AndroidResult")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!loaded.isCompleted) loaded.complete(Unit)
            }
        }
        wv.loadDataWithBaseURL("https://sozo.local/", BOOTSTRAP_HTML, "text/html", "utf-8", null)
        webView = wv
        loaded.await()
        ready = true
    }

    private suspend fun ensureRuntime() {
        if (runtimeLoaded) return
        val code = fetchCode("__runtime__") { client.get("/extractors/runtime") }
        if (code.isNotEmpty()) {
            evalOnMain(code)
            runtimeLoaded = true
        }
    }

    private suspend fun ensureExtractor(name: String, version: Int) {
        if (activeExtractor == name && activeVersion == version) return
        val code = fetchCode("$name@$version") { client.get("/extractors/$name") }
        if (code.isEmpty()) throw RuntimeException("Extractor $name is empty")
        val wrapped = buildString {
            append("(function(){try{delete globalThis.Provider;}catch(e){}\n")
            append(code)
            append("\nif(typeof Provider!=='undefined'){globalThis.Provider=Provider;}})();")
        }
        evalOnMain(wrapped)
        activeExtractor = name
        activeVersion = version
    }

    private suspend fun fetchCode(key: String, fetch: () -> String?): String {
        codeCache[key]?.let { return it }
        val code = withContext(Dispatchers.IO) { fetch() } ?: ""
        if (code.isNotEmpty()) codeCache[key] = code
        return code
    }

    private suspend fun evalOnMain(script: String) = withContext(Dispatchers.Main) {
        webView?.evaluateJavascript(script, null)
        Unit
    }

    private fun q(s: String): String = JSONObject.quote(s)

    private inner class FetchBridge {
        @JavascriptInterface
        fun fetch(id: String, reqJson: String) {
            Thread {
                val res = nativeFetch.execute(reqJson)
                webView?.post {
                    webView?.evaluateJavascript("window.__fetchResolve(${q(id)}, ${q(res)});", null)
                }
            }.start()
        }
    }

    private inner class ResultBridge {
        @JavascriptInterface
        fun onResult(id: String, json: String) {
            results[id]?.complete(JsResult(json, null))
        }

        @JavascriptInterface
        fun onError(id: String, error: String) {
            results[id]?.complete(JsResult(null, error))
        }
    }

    companion object {
        private const val BOOTSTRAP_HTML = """<!doctype html><html><head><meta charset="utf-8"></head><body><script>
window.__fetchCbs = {};
window.__fetchId = 0;
window.dartFetch = function(req){
  return new Promise(function(resolve){
    var id = String(++window.__fetchId);
    window.__fetchCbs[id] = resolve;
    try { AndroidFetch.fetch(id, JSON.stringify(req||{})); }
    catch(e){ resolve({status:0,data:null,headers:{}}); }
  });
};
window.__fetchResolve = function(id, json){
  var cb = window.__fetchCbs[id];
  if(cb){ delete window.__fetchCbs[id]; try{ cb(JSON.parse(json)); }catch(e){ cb({status:0,data:null,headers:{}}); } }
};
window.__callProvider = function(callId, fn, argsJson){
  Promise.resolve().then(function(){
    var f = (typeof Provider !== 'undefined') ? Provider[fn] : null;
    if(typeof f !== 'function') throw new Error('Provider.'+fn+' is not implemented');
    return f.apply(Provider, JSON.parse(argsJson));
  }).then(function(r){
    AndroidResult.onResult(callId, JSON.stringify(r === undefined ? null : r));
  }).catch(function(e){
    AndroidResult.onError(callId, String((e && e.message) ? e.message : e));
  });
};
</script></body></html>"""
    }
}
