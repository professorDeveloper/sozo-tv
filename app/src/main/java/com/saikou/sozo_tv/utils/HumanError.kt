package com.saikou.sozo_tv.utils

import android.content.Context
import com.saikou.sozo_tv.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * A failure, said in a way somebody can act on.
 *
 * Placeholders were printing `throwable.message` straight onto the screen. That
 * is a developer's sentence — "Unable to resolve host …", "HTTP 521" — and when
 * the exception carries no message it is literally nothing, so the placeholder
 * appeared with an empty line under its icon and explained less than saying
 * nothing at all would have.
 *
 * The cause still reaches the log; only the wording changes here.
 */
fun Context.humanError(t: Throwable?): String = when (t) {
    is UnknownHostException -> getString(R.string.error_no_internet)
    is SocketTimeoutException -> getString(R.string.error_timeout)
    is SSLException -> getString(R.string.error_secure_connection)
    is IOException -> getString(R.string.error_network)
    else -> t?.message?.takeIf { it.isNotBlank() && it.length < 120 }
        ?: getString(R.string.error_generic)
}

/** The same, for a message that already arrived as a string and may be blank. */
fun Context.humanError(message: String?): String =
    message?.takeIf { it.isNotBlank() && it.length < 120 } ?: getString(R.string.error_generic)
