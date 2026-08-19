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

class AnilistViewModel(
    private val repository: AnilistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AnilistLibraryState())
    val state: StateFlow<AnilistLibraryState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val connection: StateFlow<AnilistConnection> = repository.connection

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
                    error = (t as? AnilistException)?.message ?: "Could not load your AniList list",
                )
            }
        }
    }

    fun selectStatus(status: AnilistStatus) {
        _state.value = _state.value.copy(status = status)
    }

    fun bumpEpisode(entry: AnilistListEntry) = setProgress(entry, entry.progress + 1)

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
                replace(
                    entry.copy(progress = saved.progress, status = saved.status),
                    busy = _state.value.busy - entry.id,
                )
            } catch (t: Throwable) {
                replace(previous, busy = _state.value.busy - entry.id)
                _messages.tryEmit((t as? AnilistException)?.message ?: "Could not save to AniList")
            }
        }
    }

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
                _messages.tryEmit((t as? AnilistException)?.message ?: "Could not save to AniList")
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
    val busy: Set<Int> = emptySet(),
) {
    val visible: List<AnilistListEntry>
        get() = entries.filter { it.status == status.value }.sortedByDescending { it.updatedAt }

    val counts: Map<AnilistStatus, Int>
        get() = entries.groupingBy { AnilistStatus.fromValue(it.status) }
            .eachCount()
            .mapNotNull { (status, count) -> status?.let { it to count } }
            .toMap()
}
