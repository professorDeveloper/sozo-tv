package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Util for evaluating JavaScript in sources.
 */
@Suppress("UNUSED", "UNCHECKED_CAST")
class JavaScriptEngine(context: Context) {

    /**
     * Evaluate arbitrary JavaScript code and get the result as a primtive type
     * (e.g., String, Int).
     *
     * @since extensions-lib 1.4
     * @param script JavaScript to execute.
     * @return Result of JavaScript code as a primitive type.
     */
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.IO) {
        QuickJs.create().use {
            it.evaluate(script) as T
        }
    }
}
