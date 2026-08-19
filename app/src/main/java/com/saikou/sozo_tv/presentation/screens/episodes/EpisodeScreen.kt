package com.saikou.sozo_tv.presentation.screens.episodes

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.EpisodeTabAdapter
import com.saikou.sozo_tv.adapters.SeriesPageAdapter
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.EpisodeScreenBinding
import com.saikou.sozo_tv.parser.models.Data
import com.saikou.sozo_tv.parser.models.Part
import com.saikou.sozo_tv.parser.models.ShowResponse
import com.saikou.sozo_tv.presentation.activities.ProfileActivity
import com.saikou.sozo_tv.presentation.viewmodel.EpisodeViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.SOURCE
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.visible
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class EpisodeScreen : Fragment() {
    private var _binding: EpisodeScreenBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EpisodeViewModel by viewModel()
    private val engine: ExtensionEngine by inject()
    private val args: EpisodeScreenArgs by navArgs()
    private lateinit var adapter: SeriesPageAdapter
    private lateinit var categoriesAdapter: EpisodeTabAdapter
    private lateinit var currentMediaId: String
    private var selectedPosition = 0
    private var currentMedia: ShowResponse? = null
    private var requestedPage = -1
    private var episodeObserverBound = false
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = EpisodeScreenBinding.inflate(inflater, container, false)
        // The fragment outlives its view (player round-trip), but the observers are bound to the
        // view lifecycle — so the "already wired / already loaded" latches have to reset with it.
        episodeObserverBound = false
        requestedPage = -1
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backBtn.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.seasonalBackground.setTheme(PreferenceManager().getSeasonalTheme())
        addAnimFocus()
        val currentSource = PreferenceManager().getString(SOURCE)
        if (currentSource == "") {
            binding.topContainer.gone()
            binding.loadingLayout.gone()
            binding.textView6.gone()
            binding.textView7.gone()
            binding.placeHolder.root.visible()
            binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_source)
            binding.placeHolder.placeholderTxt.text =
                "No Source Selected \n Please Select a Source first"
            binding.placeHolder.placeHolderBtn.visible()
            binding.placeHolder.placeHolderBtn.setOnClickListener {
                val intent = Intent(requireActivity(), ProfileActivity::class.java)
                intent.putExtra("openSettings", true)
                requireActivity().startActivity(intent)
            }
        } else {
            initializeAnimeSource(currentSource)
        }
    }

    private fun initializeAnimeSource(currentSource: String) {
        // Show the real provider name (e.g. "AnimeOnsen"), not the "extension" sentinel
        // that is persisted under LocalData.SOURCE to route through the ExtensionParser.
        val displayName = engine.getActiveProviderName()?.takeIf { it.isNotBlank() } ?: currentSource
        val sourceText = "Current Selected Source: $displayName"
        binding.textView6.text = sourceText.highlightPart(
            displayName, ContextCompat.getColor(requireContext(), R.color.orange)
        )

        viewModel.loadMedia(args.mediaId, args.episodeTitle)
        viewModel.dataFound.observe(viewLifecycleOwner) { dataFound ->
            when (dataFound) {
                is Resource.Error -> {
                    binding.loadingLayout.gone()
                    binding.placeHolder.root.visible()
                    binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_network_error)
                    binding.placeHolder.placeholderTxt.text = getString(R.string.xatolik)
                }

                Resource.Loading -> {
                    binding.placeHolder.root.gone()
                    binding.topContainer.gone()
                    binding.tabRv.gone()
                    binding.loadingLayout.visible()
                    binding.loadingText.text = "Media is loading.."
                }

                is Resource.Success -> {
                    val mediaText = "Selected Media: ${dataFound.data.name}"
                    binding.textView7.gone()
                    val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
                    binding.textView7.text = mediaText.highlightPart(
                        dataFound.data.name, ContextCompat.getColor(requireContext(), R.color.red80)
                    )
                    binding.textView7.visible()
                    binding.textView7.startAnimation(anim)
                    currentMediaId = dataFound.data.link
                    currentMedia = dataFound.data
                    if (!::adapter.isInitialized) {
                        adapter = SeriesPageAdapter(localEpisode = viewModel.epListFromLocal)
                        adapter.setOnItemClickedListener { episode, index ->
                            openEpisode(episode, index)
                        }
                    }
                    // "Wrong Title?" search removed — the exact selected media is loaded directly.
                    binding.wrongTitleContainer.gone()

                    if (binding.topContainer.adapter !== adapter) {
                        binding.topContainer.adapter = adapter
                    }
                    binding.placeHolder.root.gone()
                    binding.loadingLayout.gone()
                    observeEpisodes()
                    loadPage(selectedPosition)
                }

                else -> {}
            }
        }
    }

    private fun openEpisode(episode: Data, index: Int) {
        val media = currentMedia ?: return
        findNavController().navigate(
            EpisodeScreenDirections.actionEpisodeScreenToSeriesPlayerScreen(
                id = episode.session ?: "",
                idMal = args.malId,
                name = media.name,
                currentEpisode = (episode.episode ?: 0).toString(),
                image = episode.snapshot ?: LocalData.anime404,
                seriesMainId = currentMediaId,
                currentPage = selectedPosition + 1,
                currentIndex = index
            )
        )
    }

    private fun loadPage(position: Int) {
        val media = currentMedia ?: return
        if (requestedPage == position) return
        requestedPage = position
        selectedPosition = position
        viewModel.loadEpisodeByPage(position + 1, currentMediaId, media)
    }

    @SuppressLint("SetTextI18n")
    private fun observeEpisodes() {
        // Registered once: this used to be wired up inside the dataFound observer, so every
        // re-emission stacked another observer and re-delivered the same page repeatedly.
        if (episodeObserverBound) return
        episodeObserverBound = true
        viewModel.episodeData.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Resource.Error -> {
                    // Clear the latch so re-selecting the same part retries instead of no-opping.
                    requestedPage = -1
                    binding.loadingLayout.gone()
                    binding.placeHolder.root.visible()
                    binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_network_error)
                    binding.placeHolder.placeholderTxt.text = getString(R.string.xatolik)
                }

                Resource.Loading -> {
                    binding.placeHolder.root.gone()
                    binding.loadingText.text = "Episodes are loading.."
                    // Only take the screen over while there is nothing to show. Hiding a populated
                    // grid on every page switch threw D-pad focus back out of the list.
                    if (adapter.itemCount == 0) {
                        binding.loadingLayout.visible()
                        binding.topContainer.gone()
                    }
                }

                is Resource.Success -> {
                    binding.loadingLayout.gone()
                    binding.placeHolder.root.gone()
                    val lastPage = result.data.last_page ?: return@observe
                    val episodes = result.data.data ?: return@observe
                    binding.topContainer.visible()
                    adapter.updateEpisodeItems(episodes)
                    if (lastPage <= 1) {
                        binding.tabRv.gone()
                    } else {
                        bindPartTabs(lastPage)
                    }
                }

                else -> {}
            }
        }
    }

    private fun bindPartTabs(lastPage: Int) {
        val partList = ArrayList<Part>()
        for (i in 1..lastPage) {
            partList.add(Part("Part $i", i))
        }
        binding.tabRv.visible()
        if (!::categoriesAdapter.isInitialized) {
            categoriesAdapter = EpisodeTabAdapter()
            categoriesAdapter.setFocusedItemListener { _, i -> loadPage(i) }
        }
        if (binding.tabRv.adapter !== categoriesAdapter) {
            binding.tabRv.adapter = categoriesAdapter
        }
        if (categoriesAdapter.itemCount != partList.size) {
            categoriesAdapter.submitList(partList)
        }
        categoriesAdapter.setSelectedPosition(selectedPosition)
        if (!binding.tabRv.hasFocus()) {
            binding.tabRv.scrollToPosition(selectedPosition)
        }
    }

    private fun addAnimFocus() {
        binding.backBtn.setOnFocusChangeListener { _, hasFocus ->
            val animation = when {
                hasFocus -> AnimationUtils.loadAnimation(
                    binding.root.context, R.anim.zoom_in
                )

                else -> AnimationUtils.loadAnimation(
                    binding.root.context, R.anim.zoom_out
                )
            }
            binding.backBtn.startAnimation(animation)
            animation.fillAfter = true
        }
    }

    private fun String.highlightPart(
        highlight: String, color: Int, isBold: Boolean = true
    ): SpannableString {
        val spannable = SpannableString(this)
        val start = this.indexOf(highlight)
        if (start >= 0) {
            spannable.setSpan(
                ForegroundColorSpan(color),
                start,
                start + highlight.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (isBold) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    start + highlight.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return spannable
    }

}