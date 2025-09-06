package com.saikou.sozo_tv.presentation.screens.bookmark

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.BookmarkScreenBinding
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import com.saikou.sozo_tv.presentation.viewmodel.BookmarkViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.setupGridLayoutForBookmarks
import com.saikou.sozo_tv.utils.setupGridLayoutForCategories
import com.saikou.sozo_tv.utils.toDomain
import com.saikou.sozo_tv.utils.visible
import org.koin.androidx.viewmodel.ext.android.viewModel

class BookmarkScreen : Fragment() {
    private var _binding: BookmarkScreenBinding? = null
    private val binding get() = _binding!!
    private val model: BookmarkViewModel by viewModel()
    val adapter = CategoriesPageAdapter(isDetail = true)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BookmarkScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.getAllBookmarks()
        model.getAllCharacterBookmarks()
        model.characterData.observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) {
                binding.topBar.characterTxt.text = "Characters (${it.size})"
            }
        }
        model.bookmarkData.observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) {
                binding.bookmarkRv.visible()
                binding.topBar.navAnime.setBackgroundResource(R.drawable.tab_background_selector)
                binding.topBar.navAnime.setOnClickListener {
                    binding.topBar.navAnime.setBackgroundResource(R.drawable.tab_background_selector)
                    binding.topBar.navCharacters.setBackgroundResource(R.drawable.tab_background_unselected)
                }
                binding.topBar.navCharacters.setOnClickListener {
                    binding.topBar.navCharacters.setBackgroundResource(R.drawable.tab_background_selector)
                    binding.topBar.navAnime.setBackgroundResource(R.drawable.tab_background_unselected)
                }

                binding.bookmarkPlaceHolder.root.gone()
                adapter.setClickDetail {
                    val intent = Intent(requireActivity(), PlayerActivity::class.java)
                    intent.putExtra("model", it.id)
                    requireActivity().startActivity(intent)
                }
                adapter.setCategoriesPageInterface(object :
                    CategoriesPageAdapter.CategoriesPageInterface {
                    override fun onCategorySelected(category: MainModel, position: Int) {
                    }

                })
                adapter.setCategoriesPageInterface(
                    object : CategoriesPageAdapter.CategoriesPageInterface {
                        override fun onCategorySelected(category: MainModel, position: Int) {

                        }
                    }
                )
                binding.bookmarkRv.adapter = adapter
                binding.bookmarkRv.setupGridLayoutForBookmarks(adapter)
                adapter.updateCategoriesAll(
                    it.map {
                        it.toDomain()
                    } as ArrayList<MainModel>
                )
            } else {
                binding.bookmarkRv.gone()
                binding.bookmarkPlaceHolder.root.visible()

            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        model.getAllBookmarks()
    }
}