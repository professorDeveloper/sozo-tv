package com.saikou.sozo_tv.utils

sealed class Resource<out T : Any> {
    object Idle : Resource<Nothing>()

    object Loading : Resource<Nothing>()
    data class Success<out T : Any>(val data: T) : Resource<T>()
    data class Error(val throwable: Throwable) : Resource<Nothing>()
}