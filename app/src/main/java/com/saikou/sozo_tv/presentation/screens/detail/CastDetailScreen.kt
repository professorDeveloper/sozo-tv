package com.saikou.sozo_tv.presentation.screens.detail

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.CastDetailScreenBinding
import com.saikou.sozo_tv.domain.model.CastAdapterModel
import com.saikou.sozo_tv.presentation.viewmodel.CastDetailViewModel
import com.saikou.sozo_tv.utils.snackString
import org.koin.androidx.viewmodel.ext.android.viewModel

class CastDetailScreen : Fragment(), CastDetailAdapter.DetailsInterface {
    private var _binding: CastDetailScreenBinding? = null
    private val binding get() = _binding!!

    private val model: CastDetailViewModel by viewModel()

    private val adapter = CastDetailAdapter(detailsButtonListener = this)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = CastDetailScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val args: CastDetailScreenArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.vgvMovieDetails.adapter = adapter
        model.loadDetail(args.castId ?: -1)
        model.error.observe(viewLifecycleOwner) {
            snackString(it ?: "", requireActivity())
        }
        model.castDetail.observe(viewLifecycleOwner) {
            val headerData = CastAdapterModel(
                image = it.image,
                name = it.name,
                role = it.role,
                age = it.age,
                media = it.media,
                viewType = CastDetailAdapter.DETAILS_ITEM_HEADER
            )
            val sectionData = CastAdapterModel(
                image = it.image,
                name = it.name,
                role = it.role,
                age = it.age,
                media = it.media,
                viewType = CastDetailAdapter.DETAILS_ITEM_SECTION
            )

            val thirdData = CastAdapterModel(
                image = it.image,
                name = it.name,
                role = it.role,
                age = it.age,
                media = it.media,
                viewType = CastDetailAdapter.DETAILS_ITEM_THIRD
            )
            adapter.submitList(listOf(headerData, sectionData, thirdData))
            adapter.submitRecommendedMovies(
                it.media
            )
        }
    }

    override fun onCancelButtonClicked() {
        findNavController().popBackStack()
    }


}