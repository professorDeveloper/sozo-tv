package com.saikou.sozo_tv.domain.player

import androidx.media3.common.PlaybackException

object PlayerErrorPolicy {

    enum class Kind { CODEC, SOURCE, NETWORK, GENERIC }

    sealed class Decision {
        data class SwitchSource(val toIndex: Int) : Decision()
        data class Explain(val kind: Kind, val canSwitch: Boolean) : Decision()
    }

    const val MAX_AUTO_SWITCHES = 2

    fun kindOf(errorCode: Int): Kind = when (errorCode) {
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> Kind.CODEC

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> Kind.SOURCE

        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> Kind.NETWORK

        else -> Kind.GENERIC
    }

    fun decide(
        errorCode: Int,
        currentIndex: Int,
        optionCount: Int,
        switchesUsed: Int,
        maxSwitches: Int = MAX_AUTO_SWITCHES,
    ): Decision {
        val kind = kindOf(errorCode)
        val next = currentIndex + 1
        val hasNext = optionCount > 0 && next in 0 until optionCount
        val canSwitch = optionCount > 1
        val autoSwitch = (kind == Kind.CODEC || kind == Kind.SOURCE) &&
            hasNext && switchesUsed < maxSwitches
        return if (autoSwitch) Decision.SwitchSource(next) else Decision.Explain(kind, canSwitch)
    }
}
