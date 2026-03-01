package com.saikou.sozo_tv.presentation.screens.view_all

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.saikou.sozo_tv.databinding.ViewAllScreenBinding
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import com.saikou.sozo_tv.presentation.viewmodel.ViewAllViewModel
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.setupGridLayoutForCategories
import org.koin.androidx.viewmodel.ext.android.viewModel

class ViewAllScreen : Fragment() {
    private var _binding: ViewAllScreenBinding? = null
    private val binding get() = _binding!!
    private val argData by navArgs<ViewAllScreenArgs>()
    private val viewModel: ViewAllViewModel by viewModel()
    private val adapter = CategoriesPageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = ViewAllScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewAllTitle.text = argData.argData.categoryTitle
        binding.backBtn.setOnClickListener { findNavController().popBackStack() }
        setupRecyclerView()
        setupObservers()
        viewModel.init(argData.argData.rowId)
    }

    private fun setupRecyclerView() {
        binding.viewAllRv.adapter = adapter
        binding.viewAllRv.setupGridLayoutForCategories(adapter)

        adapter.setClickDetail { item ->
            val intent = Intent(binding.root.context, PlayerActivity::class.java)
            intent.putExtra("model", item.id)
            intent.putExtra("isMovie", !item.isSeries)
            binding.root.context.startActivity(intent)
        }

        adapter.setCategoriesPageInterface(object : CategoriesPageAdapter.CategoriesPageInterface {
            override fun onCategorySelected(category: MainModel, position: Int) {
                if (viewModel.hasMore) {
                    viewModel.loadNextPage()
                }
            }
        })

    }

    private fun setupObservers() {
        viewModel.state.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Idle -> Unit

                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.viewAllRv.visibility = View.INVISIBLE
                    binding.errorTv.visibility = View.GONE
                }

                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.errorTv.visibility = View.GONE

                    adapter.updateCategoriesAll(ArrayList(resource.data))

                    if (binding.viewAllRv.visibility != View.VISIBLE) {
                        binding.viewAllRv.visibility = View.VISIBLE
                        binding.viewAllRv.startAnimation(
                            AnimationUtils.loadAnimation(requireContext(), android.R.anim.fade_in)
                        )
                    }
                }

                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.errorTv.visibility = View.VISIBLE
                    binding.errorTv.text = resource.throwable.message ?: "Xatolik yuz berdi"
                }
            }
        }

        viewModel.loadMoreState.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                }

                is Resource.Success -> {
                    adapter.updateCategories(ArrayList(resource.data))
                }

                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}