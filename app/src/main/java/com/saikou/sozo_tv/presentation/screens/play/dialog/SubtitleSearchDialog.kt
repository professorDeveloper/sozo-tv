package com.saikou.sozo_tv.presentation.screens.play.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.OnlineSubtitleAdapter
import com.saikou.sozo_tv.data.model.SubTitle
import com.saikou.sozo_tv.data.remote.device.ApiResult
import com.saikou.sozo_tv.data.remote.subtitles.OnlineSubtitle
import com.saikou.sozo_tv.data.remote.subtitles.SubtitleSearchClient
import com.saikou.sozo_tv.data.remote.subtitles.SubtitleTranslationClient
import com.saikou.sozo_tv.data.repository.DeviceAuthRepository
import com.saikou.sozo_tv.databinding.DialogSubtitleSearchBinding
import com.saikou.sozo_tv.domain.player.TranslationTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.Locale

class SubtitleSearchDialog : DialogFragment() {

    private var title: String = ""
    private var isSerial: Boolean = false
    private var season: Int? = null
    private var episode: Int? = null

    private val searchClient: SubtitleSearchClient by inject()
    private val translationClient: SubtitleTranslationClient by inject()
    private val auth: DeviceAuthRepository by inject()

    private var onPicked: ((SubTitle) -> Unit)? = null

    private var results: List<OnlineSubtitle> = emptyList()
    private var searchJob: Job? = null
    private var translating = false

    private val targetLang: String by lazy { TranslationTarget.forLocale(Locale.getDefault()) }
    private val targetName: String by lazy { TranslationTarget.displayName(targetLang) }

    private var _binding: DialogSubtitleSearchBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(
            title: String,
            isSerial: Boolean,
            season: Int?,
            episode: Int?,
        ): SubtitleSearchDialog = SubtitleSearchDialog().apply {
            this.title = title
            this.isSerial = isSerial
            this.season = season
            this.episode = episode
        }

        private val BRACKETED = Regex("""\(.*?\)""")
    }

    fun setOnSubtitlePicked(listener: (SubTitle) -> Unit) {
        onPicked = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogSubtitleSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.applyGlassWindow()

        binding.close.setOnClickListener { dismiss() }
        binding.searchQuery.setText(title.replace(BRACKETED, "").trim())
        binding.searchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search()
                true
            } else false
        }
        binding.searchBtn.setOnClickListener { search() }

        val signedIn = auth.isSignedIn()
        binding.aiTranslateBtn.isVisible = signedIn
        binding.aiTranslateLabel.text = getString(R.string.subtitle_ai_translate, targetName)
        binding.aiTranslateBtn.setOnClickListener { translate() }
        if (signedIn) loadQuota()

        binding.searchBtn.requestFocus()
        search()
    }

    private fun query(): String = binding.searchQuery.text?.toString()?.trim().orEmpty()

    private fun search() {
        if (searchJob?.isActive == true) return
        val q = query()
        if (q.isEmpty()) return
        searchJob = viewLifecycleOwner.lifecycleScope.launch { runSearch(q) }
    }

    private suspend fun runSearch(q: String) {
        binding.searchStatus.isVisible = true
        binding.searchStatus.text = getString(R.string.subtitle_search_searching)

        val result = searchClient.search(
            title = q, isSerial = isSerial, season = season, episode = episode,
        )
        val b = _binding ?: return

        results = (result as? ApiResult.Ok)?.body.orEmpty()
        b.searchStatus.text = when {
            result !is ApiResult.Ok -> getString(R.string.subtitle_search_failed)
            results.isEmpty() -> getString(R.string.subtitle_search_none)
            else -> resources.getQuantityString(R.plurals.subtitle_search_found_n, results.size, results.size)
        }
        b.resultsRv.adapter = OnlineSubtitleAdapter(results) { pick(it) }
        if (results.isEmpty()) return
        b.resultsRv.post {
            val rv = _binding?.resultsRv ?: return@post
            rv.selectedPosition = 0
            if (rv.rootView.findFocus()?.id != R.id.search_query) rv.requestFocus()
        }
    }

    private fun pick(item: OnlineSubtitle) {
        val label = item.display.ifBlank { item.language }.ifBlank { getString(R.string.subtitle_unknown_language) }
        onPicked?.invoke(SubTitle(file = item.url, label = label))
        dismiss()
    }

    private fun loadQuota() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = translationClient.quota()
            val b = _binding ?: return@launch
            val quota = (result as? ApiResult.Ok)?.body ?: return@launch
            if (!quota.enabled || quota.remaining <= 0) {
                b.aiTranslateBtn.isEnabled = false
                b.aiQuota.text = getString(R.string.subtitle_ai_none_left)
            } else {
                b.aiQuota.text = getString(R.string.subtitle_ai_quota, quota.remaining)
            }
            b.aiQuota.isVisible = true
        }
    }

    private fun translate() {
        if (translating) return
        translating = true
        viewLifecycleOwner.lifecycleScope.launch {
            searchJob?.join()
            if (_binding == null) {
                translating = false
                return@launch
            }
            if (results.isEmpty()) {
                val q = query()
                if (q.isNotEmpty()) runSearch(q)
                if (_binding == null) {
                    translating = false
                    return@launch
                }
            }
            val source = results.firstOrNull { it.language.uppercase().startsWith("EN") }
                ?: results.firstOrNull()
            if (source == null) {
                translating = false
                binding.searchStatus.isVisible = true
                binding.searchStatus.text = getString(R.string.subtitle_ai_nothing_to_translate)
                return@launch
            }

            binding.aiProgress.isVisible = true
            binding.aiTranslateBtn.isEnabled = false
            binding.searchStatus.isVisible = true
            binding.searchStatus.text = getString(R.string.subtitle_ai_translating)

            val from = if (source.language.uppercase().startsWith("EN")) "en" else null
            val result = translationClient.translate(
                url = source.url, targetLang = targetLang, from = from, title = query(),
            )
            translating = false
            val b = _binding ?: return@launch
            b.aiProgress.isVisible = false
            b.aiTranslateBtn.isEnabled = true

            when (result) {
                is ApiResult.Ok -> {
                    onPicked?.invoke(
                        SubTitle(
                            file = result.body.url,
                            label = getString(R.string.subtitle_ai_label, targetName),
                        )
                    )
                    dismiss()
                }

                is ApiResult.Http -> b.searchStatus.text =
                    result.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.subtitle_ai_failed)

                else -> b.searchStatus.text = getString(R.string.subtitle_ai_failed)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
