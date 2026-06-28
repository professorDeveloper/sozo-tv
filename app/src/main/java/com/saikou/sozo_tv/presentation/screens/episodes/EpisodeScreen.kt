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
import com.saikou.sozo_tv.parser.models.Part
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
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = EpisodeScreenBinding.inflate(inflater, container, false)
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
                    binding.placeHolder.placeholderTxt.text = dataFound.throwable.message
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
                    adapter = SeriesPageAdapter(localEpisode = viewModel.epListFromLocal)
                    // "Wrong Title?" search removed — the exact selected media is loaded directly.
                    binding.wrongTitleContainer.gone()

                    binding.topContainer.adapter = adapter
                    viewModel.loadEpisodeByPage(1, currentMediaId, dataFound.data)
                    binding.placeHolder.root.gone()
                    binding.loadingLayout.gone()
                    viewModel.episodeData.observe(viewLifecycleOwner) { result ->
                        when (result) {
                            is Resource.Error -> {
                                binding.loadingLayout.gone()
                                binding.placeHolder.root.visible()
                                binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_network_error)
                                binding.placeHolder.placeholderTxt.text = result.throwable.message
                            }

                            Resource.Loading -> {
                                binding.placeHolder.root.gone()
                                binding.loadingLayout.visible()
                                binding.topContainer.gone()
                                binding.loadingText.text = "Episodes are loading.."
                            }

                            is Resource.Success -> {
                                // Always clear the loader/placeholder once episodes resolve —
                                // the multi-page branch below previously left the spinner up.
                                binding.loadingLayout.gone()
                                binding.placeHolder.root.gone()
                                if (result.data.last_page != null && result.data.data != null) {
                                    if (result.data.last_page == 1) {
                                        binding.tabRv.gone()
                                        binding.topContainer.visible()

                                        adapter.updateEpisodeItems(result.data.data)
                                        adapter.setOnItemClickedListener { it, currentIndex ->
                                            findNavController().navigate(
                                                EpisodeScreenDirections.actionEpisodeScreenToSeriesPlayerScreen(
                                                    id = it.session ?: "",
                                                    idMal = args.mediaId,
                                                    name = dataFound.data.name,
                                                    currentEpisode = (it.episode ?: 0).toString(),
                                                    image = it.snapshot ?: LocalData.anime404,
                                                    seriesMainId = currentMediaId,
                                                    currentPage = selectedPosition + 1,
                                                    currentIndex = currentIndex
                                                )
                                            )
                                        }
                                    } else {
                                        binding.topContainer.visible()
                                        adapter.updateEpisodeItems(result.data.data)
                                        adapter.setOnItemClickedListener { it, index ->
                                            findNavController().navigate(
                                                EpisodeScreenDirections.actionEpisodeScreenToSeriesPlayerScreen(
                                                    id = it.session ?: "",
                                                    name = dataFound.data.name,
                                                    currentEpisode = (it.episode ?: 0).toString(),
                                                    image = it.snapshot ?: LocalData.anime404,
                                                    seriesMainId = currentMediaId,
                                                    currentPage = selectedPosition + 1,
                                                    currentIndex = index,
                                                    idMal = args.malId
                                                )
                                            )
                                        }
                                        val partList = ArrayList<Part>()
                                        categoriesAdapter = EpisodeTabAdapter()
                                        binding.tabRv.visible()
                                        binding.tabRv.adapter = categoriesAdapter
                                        for (i in 1..result.data.last_page) {
                                            partList.add(Part("Part $i", i))
                                        }
                                        categoriesAdapter.submitList(partList)
                                        categoriesAdapter.setSelectedPosition(
                                            selectedPosition
                                        )
                                        binding.tabRv.scrollToPosition(selectedPosition)
                                        categoriesAdapter.setFocusedItemListener { _, i ->
                                            viewModel.loadEpisodeByPage(
                                                i + 1, currentMediaId, dataFound.data
                                            )
                                            selectedPosition = i
                                        }
                                    }
                                }
                            }

                            else -> {}
                        }
                    }
                }

                else -> {}
            }
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