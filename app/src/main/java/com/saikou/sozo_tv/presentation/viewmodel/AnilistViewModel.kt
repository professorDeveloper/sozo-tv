package com.saikou.sozo_tv.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikou.sozo_tv.data.remote.anilist.AnilistException
import com.saikou.sozo_tv.data.remote.anilist.AnilistListEntry
import com.saikou.sozo_tv.data.remote.anilist.AnilistStatus
import com.saikou.sozo_tv.data.repository.AnilistConnection
import com.saikou.sozo_tv.data.repository.AnilistRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the AniList library screen.
 *
 * Holds every status in memory because AniList returns them in a single request:
 * switching filter tabs is a local operation, and re-fetching per tab would spend
 * a round trip to show data already held — which on a TV means a visible stall
 * every time the d-pad moves sideways.
 */
class AnilistViewModel(
    private val repository: AnilistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AnilistLibraryState())
    val state: StateFlow<AnilistLibraryState> = _state.asStateFlow()

    /** One-shot messages. A flow rather than state so a toast is not re-shown on rotation. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val connection: StateFlow<AnilistConnection> = repository.connection

    /**
     * Reads the connection from the account, then loads the library.
     *
     * The connection is refreshed first every time rather than trusted from a
     * previous screen: the phone can connect or disconnect at any moment, and
     * this box has no way to be told about it.
     */
    fun load(force: Boolean = false) {
        if (_state.value.loading) return
        if (_state.value.entries.isNotEmpty() && !force) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val connection = repository.refresh()
            if (connection !is AnilistConnection.Connected) {
                _state.value = AnilistLibraryState(loading = false)
                return@launch
            }
            try {
                val entries = repository.library()
                _state.value = _state.value.copy(entries = entries, loading = false, error = null)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = (t as? AnilistException)?.message ?: "AniList ro'yxati yuklanmadi",
                )
            }
        }
    }

    fun selectStatus(status: AnilistStatus) {
        _state.value = _state.value.copy(status = status)
    }

    /** Marks one more episode watched. */
    fun bumpEpisode(entry: AnilistListEntry) = setProgress(entry, entry.progress + 1)

    /**
     * Writes an explicit progress value.
     *
     * Optimistic: the card updates immediately and rolls back if the write fails.
     * On a TV the round trip is long enough that an unchanged card reads as a
     * dead button, and the remote gets pressed again.
     */
    fun setProgress(entry: AnilistListEntry, progress: Int) {
        if (progress < 0 || entry.id in _state.value.busy) return
        val previous = entry

        replace(entry.copy(progress = progress), busy = _state.value.busy + entry.id)

        viewModelScope.launch {
            try {
                val state = repository.entryState(entry.media.id)
                val saved = repository.saveProgress(
                    mediaId = entry.media.id,
                    progress = progress,
                    status = repository.statusFor(
                        current = state?.status ?: entry.status,
                        episode = progress,
                        total = state?.totalEpisodes ?: entry.media.episodes,
                    ),
                )
                // Trust the server's answer over the guess: AniList clamps progress
                // to the episode total and flips status to COMPLETED on the last one.
                replace(
                    entry.copy(progress = saved.progress, status = saved.status),
                    busy = _state.value.busy - entry.id,
                )
            } catch (t: Throwable) {
                replace(previous, busy = _state.value.busy - entry.id)
                _messages.tryEmit((t as? AnilistException)?.message ?: "Saqlanmadi")
            }
        }
    }

    /** Moves an entry to another list. */
    fun setStatus(entry: AnilistListEntry, status: AnilistStatus) {
        if (entry.id in _state.value.busy) return
        val previous = entry
        replace(entry.copy(status = status.value), busy = _state.value.busy + entry.id)

        viewModelScope.launch {
            try {
                val saved = repository.saveProgress(
                    mediaId = entry.media.id,
                    progress = entry.progress,
                    status = status.value,
                )
                replace(
                    entry.copy(progress = saved.progress, status = saved.status),
                    busy = _state.value.busy - entry.id,
                )
            } catch (t: Throwable) {
                replace(previous, busy = _state.value.busy - entry.id)
                _messages.tryEmit((t as? AnilistException)?.message ?: "Saqlanmadi")
            }
        }
    }

    private fun replace(updated: AnilistListEntry, busy: Set<Int>) {
        _state.value = _state.value.copy(
            entries = _state.value.entries.map { if (it.id == updated.id) updated else it },
            busy = busy,
        )
    }
}

data class AnilistLibraryState(
    val entries: List<AnilistListEntry> = emptyList(),
    val status: AnilistStatus = AnilistStatus.CURRENT,
    val loading: Boolean = false,
    val error: String? = null,
    /** Entry ids with a write in flight, so one slow card does not block the rest. */
    val busy: Set<Int> = emptySet(),
) {
    /**
     * The visible rows, most recently updated first — the order that puts what
     * the viewer is actually watching at the top.
     */
    val visible: List<AnilistListEntry>
        get() = entries.filter { it.status == status.value }.sortedByDescending { it.updatedAt }

    val counts: Map<AnilistStatus, Int>
        get() = entries.groupingBy { AnilistStatus.fromValue(it.status) }
            .eachCount()
            .mapNotNull { (status, count) -> status?.let { it to count } }
            .toMap()
}
