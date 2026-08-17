package com.saikou.sozo_tv.presentation.screens.anilist

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.AnilistEntryAdapter
import com.saikou.sozo_tv.adapters.AnilistStatusAdapter
import com.saikou.sozo_tv.data.repository.AnilistConnection
import com.saikou.sozo_tv.databinding.AnilistScreenBinding
import com.saikou.sozo_tv.presentation.activities.MainActivity
import com.saikou.sozo_tv.presentation.viewmodel.AnilistLibraryState
import com.saikou.sozo_tv.presentation.viewmodel.AnilistViewModel
import com.saikou.sozo_tv.utils.keepFocusAlive
import com.saikou.sozo_tv.utils.requestInitialFocus
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * The viewer's AniList library, filtered by status.
 *
 * There is no "connect" button here, and that is the design rather than an
 * omission: the OAuth handshake happens on the phone, the token is stored on the
 * Sozo account, and this box reads it. Asking someone to type an AniList password
 * with a d-pad is the problem that arrangement exists to avoid — so when there is
 * no connection, this screen says where to make one.
 */
class AnilistScreen : Fragment() {

    private var _binding: AnilistScreenBinding? = null
    private val binding get() = _binding!!

    /**
     * Scoped to the activity so [AnilistEntryDialog] shares it: the dialog writes
     * progress and the grid behind it has to show the new number the moment the
     * dialog closes.
     */
    private val model: AnilistViewModel by activityViewModel()

    private val entryAdapter = AnilistEntryAdapter { entry ->
        AnilistEntryDialog.newInstance(entry.id)
            .also { it.setOnSearchSources(::searchInSources) }
            .show(parentFragmentManager, "AnilistEntryDialog")
    }

    private lateinit var statusAdapter: AnilistStatusAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = AnilistScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusAdapter = AnilistStatusAdapter { model.selectStatus(it) }
        binding.statusRv.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.statusRv.adapter = statusAdapter

        binding.entriesRv.layoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS)
        binding.entriesRv.adapter = entryAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.state.collect(::render) }
                launch { model.connection.collect { renderConnection(it) } }
                launch {
                    model.messages.collect {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Forced on every entry to this screen: the phone can connect or
        // disconnect at any moment, and this box is never told about it.
        model.load(force = true)
    }

    private fun renderConnection(connection: AnilistConnection) {
        binding.viewerName.text = (connection as? AnilistConnection.Connected)?.viewer?.name.orEmpty()
    }

    private fun render(state: AnilistLibraryState) {
        binding.loadingBar.isVisible = state.loading

        statusAdapter.setCounts(state.counts)
        statusAdapter.setSelected(state.status)

        val connection = model.connection.value
        val message = when {
            // "Not asked yet" and "no AniList on this account" look identical to a
            // boolean, and claiming the second during the first request is wrong.
            state.loading && state.entries.isEmpty() -> null
            connection is AnilistConnection.NotConnected -> getString(R.string.anilist_connect_on_phone)
            state.error != null && state.entries.isEmpty() -> state.error
            state.visible.isEmpty() && state.entries.isEmpty() ->
                getString(R.string.anilist_library_empty)
            state.visible.isEmpty() ->
                getString(R.string.anilist_status_empty, state.status.label)
            else -> null
        }

        // Android does NOT reassign focus when the focused view is hidden or its
        // row is removed — from targetSdk 26 it clears focus to the root, and
        // the remote goes dead. Both mutations below can do exactly that: the
        // grid is hidden whenever a message replaces it, and submitList removes
        // rows when the status filter changes.
        binding.root.keepFocusAlive {
            binding.messageView.isVisible = message != null
            binding.messageView.text = message.orEmpty()
            binding.statusRv.isVisible = connection is AnilistConnection.Connected
            binding.entriesRv.isVisible = message == null

            entryAdapter.submitList(state.visible)
        }

        // First real content: put the highlight on the grid rather than leaving
        // it wherever the framework left it, which is the status row and makes
        // the screen look like a filter picker.
        if (message == null && state.visible.isNotEmpty() && !hasPlacedInitialFocus) {
            hasPlacedInitialFocus = true
            binding.entriesRv.requestInitialFocus()
        }
    }

    /** One-shot: after this, focus is the user's to move. */
    private var hasPlacedInitialFocus = false

    /**
     * Hands the title to the app's own search.
     *
     * Search lives in MainActivity's graph and this screen lives in the profile
     * one, so this crosses activities rather than navigating — which is also why
     * the query travels as an intent extra instead of a nav argument.
     */
    private fun searchInSources(title: String) {
        startActivity(
            Intent(requireContext(), MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SEARCH_QUERY, title)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        /** Poster columns. Six 150dp cards fit a 1080p TV without shrinking the art. */
        const val GRID_COLUMNS = 6
    }
}
