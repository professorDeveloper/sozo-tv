package com.saikou.sozo_tv.utils


import com.saikou.sozo_tv.domain.exceptions.ApiException
import com.saikou.sozo_tv.domain.exceptions.NotFoundException
import com.saikou.sozo_tv.domain.exceptions.ServerException
import retrofit2.Response

fun <T> Response<T>.toResult(): Result<T> {
    return when {
        this.isSuccessful && this.body() != null -> {
            Result.success(this.body()!!)
        }

        this.code() == 404 -> {
            Result.failure(NotFoundException("Resource not found: ${this.message()}"))
        }

        this.code() in 500..599 -> {
            Result.failure(ServerException("Server error (${this.code()}): ${this.message()}"))
        }

        else -> {
            Result.failure(
                ApiException(
                    "API error (${this.code()}): ${
                        this.errorBody()?.string()
                    }"
                )
            )
        }
    }
}