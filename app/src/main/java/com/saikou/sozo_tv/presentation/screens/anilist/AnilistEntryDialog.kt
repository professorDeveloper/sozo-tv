package com.saikou.sozo_tv.presentation.screens.anilist

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.AnilistStatusAdapter
import com.saikou.sozo_tv.data.remote.anilist.AnilistListEntry
import com.saikou.sozo_tv.data.remote.anilist.AnilistStatus
import com.saikou.sozo_tv.databinding.AnilistEntryDialogBinding
import com.saikou.sozo_tv.presentation.viewmodel.AnilistViewModel
import com.saikou.sozo_tv.utils.loadImage
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Everything you can do to one library entry, on one dialog.
 *
 * Re-reads the entry from the ViewModel on every state change instead of holding
 * the copy it was opened with: the dialog stays up across a write, and a captured
 * entry would keep showing the progress the user just changed.
 */
class AnilistEntryDialog : DialogFragment() {

    private var _binding: AnilistEntryDialogBinding? = null
    private val binding get() = _binding!!

    private val model: AnilistViewModel by activityViewModel()

    private val entryId: Int get() = arguments?.getInt(ARG_ENTRY_ID) ?: 0

    private var onSearchSources: ((String) -> Unit)? = null

    fun setOnSearchSources(listener: (String) -> Unit) {
        onSearchSources = listener
    }

    private lateinit var statusAdapter: AnilistStatusAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = AnilistEntryDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(0))
        dialog?.window?.setWindowAnimations(R.style.DialogAnimation)

        statusAdapter = AnilistStatusAdapter { status ->
            currentEntry()?.let { model.setStatus(it, status) }
        }
        binding.statusRv.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.statusRv.adapter = statusAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { render() }
            }
        }

        binding.btnBump.setOnClickListener {
            val entry = currentEntry() ?: return@setOnClickListener
            if (entry.nextEpisode == null) return@setOnClickListener
            model.bumpEpisode(entry)
        }

        binding.btnSearch.setOnClickListener {
            val title = currentEntry()?.media?.displayTitle ?: return@setOnClickListener
            dismiss()
            onSearchSources?.invoke(title)
        }

        binding.btnBump.requestFocus()
    }

    private fun currentEntry(): AnilistListEntry? =
        model.state.value.entries.firstOrNull { it.id == entryId }

    @SuppressLint("SetTextI18n")
    private fun render() {
        val entry = currentEntry() ?: run { dismissAllowingStateLoss(); return }
        val media = entry.media
        val busy = entry.id in model.state.value.busy

        binding.entryTitle.text = media.displayTitle
        binding.coverImage.loadImage(media.coverImage)

        val parts = buildList {
            add(AnilistStatus.fromValue(entry.status)?.label ?: entry.status)
            add(media.episodes?.let { "${entry.progress} / $it" } ?: "${entry.progress}")
            if (entry.behindBy > 0) add(getString(R.string.anilist_n_new, entry.behindBy))
        }
        binding.entryMeta.text = parts.joinToString("  •  ")

        val next = entry.nextEpisode
        binding.btnBump.text = when {
            busy -> getString(R.string.anilist_saving)
            // Caught up is stated rather than left as a button that does nothing —
            // on a remote, a dead button just gets pressed harder.
            next == null -> getString(R.string.anilist_caught_up)
            else -> getString(R.string.anilist_mark_watched, next)
        }
        binding.btnBump.isEnabled = !busy && next != null
        binding.btnBump.alpha = if (binding.btnBump.isEnabled) 1f else 0.5f

        statusAdapter.setCounts(emptyMap())
        AnilistStatus.fromValue(entry.status)?.let { statusAdapter.setSelected(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ENTRY_ID = "entryId"

        fun newInstance(entryId: Int) = AnilistEntryDialog().apply {
            arguments = Bundle().apply { putInt(ARG_ENTRY_ID, entryId) }
        }
    }
}
