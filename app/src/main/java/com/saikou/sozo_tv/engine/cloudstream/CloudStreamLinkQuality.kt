package com.saikou.sozo_tv.engine.cloudstream

/** CloudStream's Unknown sentinel is 400, not a pixel height. */
internal object CloudStreamLinkQuality {
    fun isKnown(height: Int): Boolean = height in 144..4320 && height != 400
    fun rank(height: Int, type: String): Int = when {
        isKnown(height) -> height
        type == "hls" || type == "dash" -> 1000
        else -> 0
    }
}
