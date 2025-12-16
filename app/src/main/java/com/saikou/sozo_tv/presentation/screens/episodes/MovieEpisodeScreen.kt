package com.saikou.sozo_tv.presentation.screens.episodes

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.EpisodeTabAdapter
import com.saikou.sozo_tv.adapters.SeriesPageAdapter
import com.saikou.sozo_tv.databinding.EpisodeScreenBinding
import com.saikou.sozo_tv.parser.models.Part
import com.saikou.sozo_tv.parser.movie.PlayImdb
import com.saikou.sozo_tv.presentation.activities.ProfileActivity
import com.saikou.sozo_tv.presentation.viewmodel.EpisodeViewModel
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.visible
import org.koin.androidx.viewmodel.ext.android.viewModel

class MovieEpisodeScreen : Fragment() {
    private var _binding: EpisodeScreenBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EpisodeViewModel by viewModel()
    private val args: MovieEpisodeScreenArgs by navArgs()
    private lateinit var adapter: SeriesPageAdapter
    private lateinit var categoriesAdapter: EpisodeTabAdapter
    private lateinit var currentMediaId: String
    private var selectedPosition = 0
    private var currentSeason = 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = EpisodeScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var isFirst = false

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        categoriesAdapter = EpisodeTabAdapter()

        binding.backBtn.setOnClickListener {
            findNavController().popBackStack()
        }

        addAnimFocus()

        val currentSource = readData("movieSource") ?: "playimdb"
        if (currentSource != "playimdb") {
            binding.topContainer.gone()
            binding.loadingLayout.gone()
            binding.textView6.gone()
            binding.textView7.gone()
            binding.placeHolder.root.visible()
            binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_source)
            binding.placeHolder.placeholderTxt.text =
                "No Source Selected \n Please Select Source First from Settings"
            binding.placeHolder.placeHolderBtn.visible()
            binding.placeHolder.placeHolderBtn.setOnClickListener {
                val intent = Intent(requireActivity(), ProfileActivity::class.java)
                intent.putExtra("openSettings", true)
                requireActivity().startActivity(intent)
            }
        } else {
            val sourceText = "Current Selected Source:${PlayImdb::class.java.simpleName}"
            binding.textView6.text = sourceText.highlightPart(
                PlayImdb::class.java.simpleName,
                ContextCompat.getColor(requireContext(), R.color.orange)
            )
            viewModel.findImdbIdSeries(args.tmdbId.toString(), args.title, args.image, args.isMovie)
            viewModel.dataFound.observe(viewLifecycleOwner) { dataFound ->
                when (dataFound) {
                    is Resource.Error -> {
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
                        val anim =
                            AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
                        binding.textView7.text = mediaText.highlightPart(
                            dataFound.data.name,
                            ContextCompat.getColor(requireContext(), R.color.red80)
                        )
                        binding.textView7.visible()
                        binding.textView7.startAnimation(anim)
                        currentMediaId = dataFound.data.link
                        adapter = SeriesPageAdapter()
                        binding.topContainer.adapter = adapter
                        viewModel.loadMovieSeriesEpisodes(
                            currentMediaId,
                            tmdbId = args.tmdbId,
                            currentSeason,
                            args.isMovie,
                            args.image
                        )
                        binding.placeHolder.root.gone()
                        binding.loadingLayout.visible()
                        viewModel.episodeData.observe(viewLifecycleOwner) { result ->
                            when (result) {
                                is Resource.Error -> {
                                    binding.placeHolder.root.visible()
                                    binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_network_error)
                                    binding.placeHolder.placeholderTxt.text =
                                        result.throwable.message
                                }

                                Resource.Loading -> {
                                    binding.placeHolder.root.gone()
                                    binding.loadingLayout.visible()
                                    binding.topContainer.gone()
                                    binding.loadingText.text = "Episodes are loading.."
                                }

                                is Resource.Success -> {

                                    binding.placeHolder.root.gone()
                                    binding.topContainer.visible()
                                    binding.loadingLayout.gone()
                                    adapter.updateEpisodeItems(
                                        result.data.data ?: arrayListOf()
                                    )
                                    adapter.setOnItemClickedListener { it, currentIndex ->
                                        Log.d("GGG", "onViewCreated:${it.season} ")
                                        findNavController().navigate(
                                            MovieEpisodeScreenDirections.actionMovieEpisodeScreenToMovieSeriesPlayerScreen(
                                                args.tmdbId,
                                                args.isMovie,
                                                dataFound.data.link,
                                                selectedPosition + 1,
                                                currentIndex,
                                                args.title,
                                                args.image,
                                                it.session ?: "",
                                                currentSeason,
                                                it.episode?.toInt() ?: -1
                                            )
                                        )
                                    }
                                }

                                else -> {}
                            }
                        }
                        viewModel.firstCategoryDataObserver.observe(viewLifecycleOwner) {
                            if (!isFirst) {
                                val partList = ArrayList<Part>()


                                binding.tabRv.visible()
                                binding.tabRv.adapter = categoriesAdapter
                                viewModel.cachedSeasons.keys.forEach {
                                    partList.add(Part("Season $it", it))
                                }
                                categoriesAdapter.submitList(partList)
                                categoriesAdapter.setSelectedPosition(
                                    selectedPosition
                                )
                                binding.tabRv.scrollToPosition(selectedPosition)
                                categoriesAdapter.setFocusedItemListener { item, i ->
                                    viewModel.loadMovieSeriesEpisodes(
                                        dataFound.data.link, args.tmdbId, item.part,
                                        args.isMovie,
                                        args.image
                                    )
                                    currentSeason = (item.part)
                                    selectedPosition = i
                                }
                                isFirst = true
                            }
                        }
                    }

                    else -> {}
                }
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
        binding.wrongTitleContainer.setOnFocusChangeListener { _, hasFocus ->
            val animation = when {
                hasFocus -> AnimationUtils.loadAnimation(
                    binding.root.context, R.anim.zoom_in
                )

                else -> AnimationUtils.loadAnimation(
                    binding.root.context, R.anim.zoom_out
                )
            }
            binding.wrongTitleContainer.startAnimation(animation)
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


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        isFirst = false
    }
}