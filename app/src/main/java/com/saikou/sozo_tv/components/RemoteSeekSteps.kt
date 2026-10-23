package com.saikou.sozo_tv.components

/** Signed seek deltas for a run of remote key presses; changing direction resets acceleration. */
internal class RemoteSeekSteps {
    private var direction = 0
    private var count = 0
    private var lastAt = 0L

    fun next(direction: Int, nowMs: Long): Long {
        require(direction == -1 || direction == 1)
        if (direction != this.direction || nowMs - lastAt > 700L || nowMs < lastAt) {
            this.direction = direction
            count = 0
        }
        lastAt = nowMs
        val step = STEPS[count.coerceAtMost(STEPS.lastIndex)]
        count = (count + 1).coerceAtMost(STEPS.lastIndex)
        return direction * step
    }

    private companion object {
        val STEPS = longArrayOf(10_000L, 30_000L, 60_000L, 120_000L)
    }
}
