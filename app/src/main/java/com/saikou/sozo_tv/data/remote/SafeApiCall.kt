package com.saikou.sozo_tv.data.remote

import com.bugsnag.android.Bugsnag
import com.saikou.sozo_tv.domain.exceptions.NetworkException
import com.saikou.sozo_tv.domain.exceptions.UnknownException
import com.saikou.sozo_tv.utils.toResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.IOException
import retrofit2.Response

suspend fun <T> safeApiCall(
    retries: Int = 2,
    delayMillis: Long = 1500,
    apiCall: suspend () -> Response<T>
): Result<T> {
    return withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        repeat(retries + 1) { attempt ->
            try {
                val response = apiCall()
                return@withContext response.toResult()
            } catch (e: IOException) {
                lastException = e
                if (attempt < retries) {
                    delay(delayMillis * (attempt + 1))
                }
            } catch (e: Exception) {
                lastException = e
                return@withContext Result.failure(UnknownException("Unexpected error: ${e.message}"))
            }
        }

        Bugsnag.notify(lastException ?: Exception("nothing"))
        Result.failure(NetworkException("Network error: ${lastException?.message}"))
    }
}
