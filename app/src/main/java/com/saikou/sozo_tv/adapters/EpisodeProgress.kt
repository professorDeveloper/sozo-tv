package com.saikou.sozo_tv.adapters

internal fun episodeProgressPercent(watched: Pair<Long, Long>?): Int? {
    if (watched == null || watched.second <= 0L) return null
    return (watched.first.toDouble() / watched.second * 100).toInt().coerceIn(0, 100)
}
