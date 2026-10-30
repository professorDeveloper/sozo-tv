package com.saikou.sozo_tv.debug

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.presentation.activities.MainActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** Debug-only real plugin runtime search/detail probe, with no credential or signed URL output. */
class CloudStreamProbeInstrumentation : Instrumentation() {
    private var arguments = Bundle()
    override fun onCreate(arguments: Bundle?) { this.arguments = arguments ?: Bundle(); super.onCreate(arguments); start() }
    override fun onStart() {
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val results = JSONArray()
        runBlocking {
            for (provider in arguments.getString("providers", "StreamPlay,Anikage,IPTV Player").split(',')) {
                val start = System.currentTimeMillis()
                val result = JSONObject().put("provider", provider)
                try {
                    withTimeout(45_000) {
                        val page = ExtensionEngine.shared.search("cs:$provider", arguments.getString("query", "Avatar"))
                        result.put("items", page?.items?.size ?: 0)
                        result.put("status", "SEARCH_OK")
                        if (arguments.getString("detail", "false") == "true") {
                            val first = page?.items?.firstOrNull() ?: error("No results")
                            val detail = ExtensionEngine.shared.load("cs:$provider", first.contentUrl) ?: error("No details")
                            result.put("episodes", detail.episodes.size).put("type", detail.type)
                        }
                    }
                } catch (t: Throwable) { result.put("status", "FAIL").put("error", "${t.javaClass.simpleName}: ${t.message}") }
                result.put("ms", System.currentTimeMillis() - start); results.put(result)
            }
        }
        runOnMainSync { activity.finish() }
        targetContext.filesDir.resolve("cloudstream-probe.json").writeText(results.toString(2))
        finish(0, Bundle().apply { putString("result", results.toString()) })
    }
}
