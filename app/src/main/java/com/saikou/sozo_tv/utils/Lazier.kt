package com.saikou.sozo_tv.utils

import kotlin.reflect.KFunction

data class Lazier<T>(
    val lClass: KFunction<T>,
    val name: String
) {
    val get = lazy { lClass.call() }
}

fun <T> lazyList(vararg objects: Pair<String, KFunction<T>>): List<Lazier<T>> {
    return objects.map {
        Lazier(it.second, it.first)
    }
}

fun <T> T.printIt(pre: String = ""): T {
    println("$pre$this")
    return this
}
