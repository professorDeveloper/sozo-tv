package com.saikou.sozo_tv.presentation.screens.episodes

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.SeriesPageAdapter
import com.saikou.sozo_tv.databinding.EpisodeScreenBinding
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
        if (currentSource.isEmpty()) {
            binding.topContainer.gone()
            binding.loadingLayout.gone()
            binding.textView6.gone()
            binding.placeHolder.root.visible()
            binding.placeHolder.placeHolderImg.setImageResource(R.drawable.ic_source)
            binding.placeHolder.placeholderTxt.text =
                "No Source Selected \n Please Select Source First "
        } else {
            binding.textView6.text = "Current Selected Source: $currentSource"
            viewModel.findEpisodes(args.episodeTitle, )
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
                                    if (it.data.last_page == 1) {
                                        adapter = SeriesPageAdapter()
                                        binding.tabRv.gone()
                                        binding.placeHolder.root.gone()
                                        binding.loadingLayout.gone()
                                        binding.topContainer.adapter = adapter
                                        adapter.updateEpisodeItems(it.data.data ?: arrayListOf())

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