package com.saikou.sozo_tv.data.repository

import android.util.Log
import com.saikou.sozo_tv.data.local.pref.DeviceSessionStore
import com.saikou.sozo_tv.data.remote.remote.RemoteCommand
import com.saikou.sozo_tv.data.remote.remote.RemoteControlClient
import com.saikou.sozo_tv.data.remote.remote.RemoteState
import com.saikou.sozo_tv.data.remote.remote.RemoteStreamException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import android.content.Intent
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.presentation.activities.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Keeps the phone's channel to this TV open, and fans commands out to whoever
 * is on screen.
 *
 * One connection for the whole app, owned here rather than by a screen: the
 * channel has to survive navigation, and a phone pressing play while the TV
 * sits on the home screen is the normal case, not an edge one.
 */
class RemoteControlManager(
    private val client: RemoteControlClient,
    private val store: DeviceSessionStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    // extraBufferCapacity, not replay: a command that arrived before a screen
    // was listening is stale by the time it gets there, and replaying it would
    // re-press a button the user pressed minutes ago.
    private val _commands = MutableSharedFlow<RemoteCommand>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val commands: SharedFlow<RemoteCommand> get() = _commands

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> get() = _connected

    /**
     * The last "take me somewhere" command, held until something acts on it.
     *
     * [_commands] has no replay, and a SharedFlow with no subscriber drops what
     * it is given — so a phone pressing "Play on TV" while the TV sat on the
     * profile page or in the player sent a command that landed nowhere, and the
     * phone still said it had been sent. Playback commands are right to be
     * dropped (a play from three minutes ago is not worth honouring); navigation
     * is not, because it is the whole point of the button.
     */
    private val _navigation = MutableStateFlow<RemoteCommand?>(null)
    val navigation: StateFlow<RemoteCommand?> get() = _navigation

    /** Call once a navigation command has been acted on. */
    fun consumeNavigation(command: RemoteCommand) {
        _navigation.compareAndSet(command, null)
    }

    /** Starts following the session. Safe to call more than once. */
    fun start() {
        if (loop != null) return
        loop = scope.launch {
            // collectLatest so signing out cancels the in-flight connection
            // instead of leaving it holding a socket for a session that is gone.
            store.session.collectLatest { session ->
                _connected.value = false
                val deviceId = session?.sessionId
                if (deviceId.isNullOrBlank()) return@collectLatest
                connectForever(deviceId)
            }
        }
    }

    suspend fun stop() {
        loop?.cancelAndJoin()
        loop = null
        _connected.value = false
    }

    private suspend fun connectForever(deviceId: String) {
        var backoffMs = MIN_BACKOFF_MS

        while (coroutineContext.isActive) {
            try {
                client.stream(
                    deviceId = deviceId,
                    onOpen = {
                        _connected.value = true
                        // Reset only once the server actually accepted us. Resetting
                        // on attempt would turn a refused connection into a tight loop.
                        backoffMs = MIN_BACKOFF_MS
                    },
                    onCommand = { command ->
                        if (command.type in NAVIGATION_TYPES) {
                            _navigation.value = command
                            // Whatever is on screen, the phone asked for this TV
                            // to go somewhere — so bring the screen that can do
                            // it forward instead of waiting to be returned to.
                            bringMainToFront()
                        }
                        if (!_commands.tryEmit(command)) {
                            Log.w(TAG, "command dropped, nobody listening: ${command.type}")
                        }
                    },
                )
                // Returned cleanly: the server closed the stream. Reconnect.
                Log.d(TAG, "stream closed by server")
            } catch (e: RemoteStreamException) {
                _connected.value = false
                if (e.code == 404) {
                    // This device session no longer exists. Retrying cannot fix
                    // it; the session flow will restart us if a new one arrives.
                    Log.w(TAG, "device session gone, giving up until re-paired")
                    return
                }
                // 401 included: accessToken() refreshes on the next attempt.
                Log.w(TAG, "stream rejected: ${e.code}")
            } catch (e: IOException) {
                Log.d(TAG, "stream dropped: ${e.message}")
            } catch (e: IllegalStateException) {
                Log.w(TAG, "no token yet: ${e.message}")
            }

            _connected.value = false
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    /** Reports what is on screen. Fire and forget — never block playback for this. */
    fun report(
        screen: String,
        title: String? = null,
        episode: String? = null,
        playing: Boolean = false,
        positionMs: Long? = null,
        durationMs: Long? = null,
    ) {
        val deviceId = store.current()?.sessionId ?: return
        if (!_connected.value) return
        scope.launch {
            client.report(
                RemoteState(
                    deviceId = deviceId,
                    screen = screen,
                    title = title,
                    episode = episode,
                    playing = playing,
                    positionMs = positionMs,
                    durationMs = durationMs,
                )
            )
        }
    }

    /**
     * Puts the navigating screen in front, from wherever the TV happens to be.
     *
     * REORDER_TO_FRONT rather than a fresh start: MainActivity is already in the
     * task in every normal case, and restarting it would throw away the back
     * stack the viewer built.
     */
    private fun bringMainToFront() {
        val context = MyApp.context
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "could not bring the main screen forward: ${it.message}") }
    }

    companion object {
        private const val TAG = "RemoteControl"
        private const val MIN_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L

        /** Commands that move the TV around, as opposed to driving playback. */
        private val NAVIGATION_TYPES = setOf("open", "text", "home")
    }
}
