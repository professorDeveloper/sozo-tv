package com.saikou.sozo_tv.cloudstream

import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        AllMovieLandProvider().search("Wednesday").let {
            println(it)
        }
    }
}