package com.saikou.sozo_tv.data.remote

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Query
import kotlinx.coroutines.delay

suspend fun <D : Query.Data> ApolloClient.safeExecute(
    query: Query<D>,
    retries: Int = 3,
    delayMillis: Long = 1000L
): D? {
    repeat(retries) { attempt ->
        try {
            val response = this.query(query).execute()
            if (!response.hasErrors()) {
                return response.data
            } else if (response.errors?.any { it.message.contains("Too Many Requests") } == true) {
                delay(delayMillis * (attempt + 1)) // exponential backoff
            } else {
                throw Exception(response.errors?.first()?.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            if (attempt == retries - 1) throw e
            delay(delayMillis * (attempt + 1))
        }
    }
    return null
}

suspend fun <T> safeExecute(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(e)
    }
}
