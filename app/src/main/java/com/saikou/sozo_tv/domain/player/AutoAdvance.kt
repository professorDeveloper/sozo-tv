package com.saikou.sozo_tv.domain.player

object AutoAdvance {

    const val COUNTDOWN_MS = 10_000L
    const val COUNTDOWN_SECONDS = (COUNTDOWN_MS / 1000L).toInt()

    private const val WATCHED_FRACTION = 0.9

    fun shouldStart(positionMs: Long, durationMs: Long, alreadyShown: Boolean): Boolean {
        if (alreadyShown || durationMs <= 0 || positionMs <= 0) return false
        val remaining = durationMs - positionMs
        return remaining in 1..COUNTDOWN_MS
    }

    fun resumePosition(savedMs: Long, savedDurationMs: Long): Long {
        if (savedMs <= 0) return 0L
        if (savedDurationMs > 0 && savedMs >= savedDurationMs * WATCHED_FRACTION) return 0L
        return savedMs
    }
}
