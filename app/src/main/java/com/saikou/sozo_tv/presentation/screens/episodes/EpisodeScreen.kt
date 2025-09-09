package com.saikou.sozo_tv.presentation.screens.episodes

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.EpisodeTabAdapter
import com.saikou.sozo_tv.adapters.SeriesPageAdapter
import com.saikou.sozo_tv.databinding.EpisodeScreenBinding
import com.saikou.sozo_tv.parser.models.Part
import com.saikou.sozo_tv.presentation.viewmodel.EpisodeViewModel
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.readData
import com.saikou.sozo_tv.utils.visible
import org.koin.androidx.viewmodel.ext.android.viewModel

class EpisodeScreen : Fragment() {
    private var _binding: EpisodeScreenBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EpisodeViewModel by viewModel()
    private val args: EpisodeScreenArgs by navArgs()
    private lateinit var adapter: SeriesPageAdapter
    private lateinit var categoriesAdapter: EpisodeTabAdapter
    private lateinit var currentMediaId: String
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = EpisodeScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentSource = readData<String>("subSource") ?: ""
        if (currentSource =="animepahe") {
            binding.topContainer.gone()
            binding.loadingLayout.gone()
            binding.textView6.gone()
            binding.placeHolder.root.visible()
            binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_source)
            binding.placeHolder.placeholderTxt.text =
                "No Source Selected \n Please Select Source First "
        } else {
            binding.textView6.text = "Current Selected Source: $currentSource"
            viewModel.findEpisodes(args.episodeTitle)
            viewModel.dataFound.observe(viewLifecycleOwner) {
                when (it) {
                    is Resource.Error -> {
                        binding.placeHolder.root.visible()
                        binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_network_error)
                        binding.placeHolder.placeholderTxt.text = it.throwable.message
                    }

                    Resource.Loading -> {
                        binding.placeHolder.root.gone()
                        binding.loadingLayout.visible()
                        binding.loadingText.text = "Media is loading.."
                    }

                    is Resource.Success -> {
                        currentMediaId = it.data.link
                        adapter = SeriesPageAdapter()
                        binding.topContainer.adapter = adapter
                        viewModel.loadEpisodeByPage(1, currentMediaId)
                        binding.placeHolder.root.gone()
                        binding.loadingLayout.gone()
                        viewModel.episodeData.observe(viewLifecycleOwner) {
                            when (it) {
                                is Resource.Error -> {
                                    binding.placeHolder.root.visible()
                                    binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_network_error)
                                    binding.placeHolder.placeholderTxt.text = it.throwable.message
                                }

                                Resource.Loading -> {
                                    binding.placeHolder.root.gone()
                                    binding.loadingLayout.visible()
                                    binding.loadingText.text = "Episodes are loading.."

                                }

                                is Resource.Success -> {
                                    if (it.data.last_page != null && it.data.data != null) {
                                        if (it.data.last_page == 1) {
                                            binding.tabRv.gone()
                                            binding.placeHolder.root.gone()
                                            binding.loadingLayout.gone()
                                            adapter.updateEpisodeItems(
                                                it.data.data
                                            )

                                        } else {
                                            adapter.updateEpisodeItems(
                                                it.data.data
                                            )
                                            val partList = ArrayList<Part>()
                                            categoriesAdapter = EpisodeTabAdapter()
                                            binding.tabRv.visible()
                                            binding.tabRv.adapter = categoriesAdapter
                                            for (i in 1..it.data.last_page) {
                                                partList.add(
                                                    Part(
                                                        "Part $i",
                                                        i,
                                                    )
                                                )
                                            }
                                            categoriesAdapter.submitList(partList)
                                            categoriesAdapter.setSelectedPosition(0)
                                            categoriesAdapter.setFocusedItemListener { part, i ->
                                                viewModel.loadEpisodeByPage(
                                                    i + 1,
                                                    currentMediaId
                                                )
                                            }
                                        }
                                    }

                                }

                                else -> {

                                }
                            }
                        }
                    }

                    else -> {

                    }
                }
            }
        }
    }
}