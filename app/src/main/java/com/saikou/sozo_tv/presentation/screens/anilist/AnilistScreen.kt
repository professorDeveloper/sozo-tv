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

class AnilistScreen : Fragment() {

    private var _binding: AnilistScreenBinding? = null
    private val binding get() = _binding!!

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
            state.loading && state.entries.isEmpty() -> null
            connection is AnilistConnection.NotConnected -> getString(R.string.anilist_connect_on_phone)
            state.error != null && state.entries.isEmpty() -> state.error
            state.visible.isEmpty() && state.entries.isEmpty() ->
                getString(R.string.anilist_library_empty)
            state.visible.isEmpty() ->
                getString(R.string.anilist_status_empty, state.status.label)
            else -> null
        }

        binding.root.keepFocusAlive {
            binding.messageView.isVisible = message != null
            binding.messageView.text = message.orEmpty()
            binding.statusRv.isVisible = connection is AnilistConnection.Connected
            binding.entriesRv.isVisible = message == null

            entryAdapter.submitList(state.visible)
        }

        if (message == null && state.visible.isNotEmpty() && !hasPlacedInitialFocus) {
            hasPlacedInitialFocus = true
            binding.entriesRv.requestInitialFocus()
        }
    }

    private var hasPlacedInitialFocus = false

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
        const val GRID_COLUMNS = 6
    }
}
