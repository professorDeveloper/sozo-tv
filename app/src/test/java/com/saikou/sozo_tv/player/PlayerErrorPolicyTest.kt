package com.saikou.sozo_tv.player

import androidx.media3.common.PlaybackException
import com.saikou.sozo_tv.domain.player.PlayerErrorPolicy
import com.saikou.sozo_tv.domain.player.PlayerErrorPolicy.Decision
import com.saikou.sozo_tv.domain.player.PlayerErrorPolicy.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerErrorPolicyTest {

    private val codec = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
    private val badSource = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    private val network = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

    @Test
    fun `a codec failure moves to the next source while sources and tries remain`() {
        assertEquals(
            Decision.SwitchSource(1),
            PlayerErrorPolicy.decide(codec, currentIndex = 0, optionCount = 3, switchesUsed = 0),
        )
    }

    @Test
    fun `a dead link is treated like a codec failure`() {
        assertEquals(
            Decision.SwitchSource(2),
            PlayerErrorPolicy.decide(badSource, currentIndex = 1, optionCount = 3, switchesUsed = 1),
        )
    }

    @Test
    fun `the last source explains itself instead of switching`() {
        assertEquals(
            Decision.Explain(Kind.CODEC, canSwitch = true),
            PlayerErrorPolicy.decide(codec, currentIndex = 2, optionCount = 3, switchesUsed = 0),
        )
    }

    @Test
    fun `switching stops after the budget is spent`() {
        assertEquals(
            Decision.Explain(Kind.SOURCE, canSwitch = true),
            PlayerErrorPolicy.decide(badSource, currentIndex = 0, optionCount = 5, switchesUsed = 2),
        )
    }

    @Test
    fun `a network error never switches on its own`() {
        assertEquals(
            Decision.Explain(Kind.NETWORK, canSwitch = true),
            PlayerErrorPolicy.decide(network, currentIndex = 0, optionCount = 3, switchesUsed = 0),
        )
    }

    @Test
    fun `a single source cannot offer a switch`() {
        assertEquals(
            Decision.Explain(Kind.CODEC, canSwitch = false),
            PlayerErrorPolicy.decide(codec, currentIndex = 0, optionCount = 1, switchesUsed = 0),
        )
    }

    @Test
    fun `an unknown code is generic`() {
        assertEquals(Kind.GENERIC, PlayerErrorPolicy.kindOf(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }
}
