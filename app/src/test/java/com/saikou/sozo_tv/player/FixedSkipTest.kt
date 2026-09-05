package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.components.SkipIntroView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedSkipTest {
    private fun target(positionMs: Long, durationMs: Long): Long =
        (positionMs + SkipIntroView.FIXED_SKIP_SECONDS * 1000L)
            .coerceAtMost(maxOf(durationMs - 1000L, 0L))

    @Test
    fun `the jump is the announced length`() {
        assertEquals(85L, SkipIntroView.FIXED_SKIP_SECONDS)
    }

    @Test
    fun `skipping moves forward from where you are, not to a fixed point`() {
        assertEquals(85_000L, target(0L, 1_400_000L))
        assertEquals(145_000L, target(60_000L, 1_400_000L))
        assertEquals(685_000L, target(600_000L, 1_400_000L))
    }

    @Test
    fun `it never seeks past the end of the episode`() {
        val duration = 100_000L
        assertTrue(target(90_000L, duration) < duration)
        assertEquals(99_000L, target(90_000L, duration))
    }

    @Test
    fun `an unknown duration still produces a legal seek`() {
        assertTrue(target(10_000L, -1L) >= 0L)
        assertEquals(0L, target(10_000L, 0L))
    }

    @Test
    fun `the offer window starts after the cold open and ends with the opening`() {
        assertTrue(SkipIntroView.FIXED_SKIP_WINDOW_START > 0L)
        assertTrue(SkipIntroView.FIXED_SKIP_WINDOW_END < 30 * 60_000L)
        assertTrue(
            SkipIntroView.FIXED_SKIP_WINDOW_START < SkipIntroView.FIXED_SKIP_WINDOW_END,
        )
    }

    @Test
    fun `the window is long enough to cover a late opening`() {
        assertTrue(SkipIntroView.FIXED_SKIP_WINDOW_END >= 180_000L)
    }
}
