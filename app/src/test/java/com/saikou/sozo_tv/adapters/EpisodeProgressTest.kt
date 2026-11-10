package com.saikou.sozo_tv.adapters

import org.junit.Assert.*
import org.junit.Test

class EpisodeProgressTest {
    @Test fun unknownDurationDoesNotDivideByZeroOrShowWatchedBar() {
        assertNull(episodeProgressPercent(1000L to 0L))
        assertNull(episodeProgressPercent(1000L to -1L))
        assertNull(episodeProgressPercent(null))
    }

    @Test fun clampsProgressAndDoesNotOverflowLongMultiplication() {
        assertEquals(0, episodeProgressPercent(-1L to 100L))
        assertEquals(50, episodeProgressPercent(50L to 100L))
        assertEquals(100, episodeProgressPercent(110L to 100L))
        assertEquals(50, episodeProgressPercent(Long.MAX_VALUE / 2 to Long.MAX_VALUE))
    }
}
