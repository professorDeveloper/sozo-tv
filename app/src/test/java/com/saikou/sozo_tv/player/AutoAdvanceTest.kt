package com.saikou.sozo_tv.player

import com.saikou.sozo_tv.domain.player.AutoAdvance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoAdvanceTest {

    private val duration = 1_400_000L

    @Test
    fun `the countdown starts anywhere inside the last ten seconds`() {
        assertTrue(AutoAdvance.shouldStart(duration - 10_000L, duration, alreadyShown = false))
        assertTrue(AutoAdvance.shouldStart(duration - 4_321L, duration, alreadyShown = false))
        assertTrue(AutoAdvance.shouldStart(duration - 1L, duration, alreadyShown = false))
    }

    @Test
    fun `it does not start early, twice, or without a duration`() {
        assertFalse(AutoAdvance.shouldStart(duration - 10_001L, duration, alreadyShown = false))
        assertFalse(AutoAdvance.shouldStart(duration - 5_000L, duration, alreadyShown = true))
        assertFalse(AutoAdvance.shouldStart(5_000L, -1L, alreadyShown = false))
        assertFalse(AutoAdvance.shouldStart(0L, duration, alreadyShown = false))
    }

    @Test
    fun `a partly watched episode resumes where it stopped`() {
        assertEquals(600_000L, AutoAdvance.resumePosition(600_000L, duration))
    }

    @Test
    fun `an episode watched to the end starts over`() {
        assertEquals(0L, AutoAdvance.resumePosition(duration - 30_000L, duration))
        assertEquals(0L, AutoAdvance.resumePosition(0L, duration))
        assertEquals(0L, AutoAdvance.resumePosition(-5L, duration))
    }

    @Test
    fun `an unknown saved duration still resumes`() {
        assertEquals(600_000L, AutoAdvance.resumePosition(600_000L, 0L))
    }
}
