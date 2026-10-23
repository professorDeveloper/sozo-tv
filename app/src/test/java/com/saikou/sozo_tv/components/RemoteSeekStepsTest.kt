package com.saikou.sozo_tv.components

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteSeekStepsTest {
    @Test fun rewindAlwaysProducesNegativeDeltas() {
        val steps = RemoteSeekSteps()
        assertEquals(-10_000L, steps.next(-1, 1000))
        assertEquals(-30_000L, steps.next(-1, 1100))
        assertEquals(-60_000L, steps.next(-1, 1200))
        repeat(100) { assertEquals(-120_000L, steps.next(-1, 1300L + it)) }
    }

    @Test fun directionChangeAndPauseResetAcceleration() {
        val steps = RemoteSeekSteps()
        assertEquals(10_000L, steps.next(1, 1000))
        assertEquals(30_000L, steps.next(1, 1100))
        assertEquals(-10_000L, steps.next(-1, 1200))
        assertEquals(-30_000L, steps.next(-1, 1300))
        assertEquals(-10_000L, steps.next(-1, 2100))
    }
}
