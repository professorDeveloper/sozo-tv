package com.saikou.sozo_tv.presentation.screens.bookmark

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ConcatAdapter
import com.saikou.sozo_tv.adapters.CharactersPageAdapter
import com.saikou.sozo_tv.adapters.TabAdapter
import com.saikou.sozo_tv.databinding.BookmarkScreenBinding
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import com.saikou.sozo_tv.presentation.viewmodel.BookmarkViewModel
import com.saikou.sozo_tv.utils.setupGridLayoutForBookmarks
import com.saikou.sozo_tv.utils.toDomain
import org.koin.androidx.viewmodel.ext.android.viewModel

class BookmarkScreen : Fragment() {
    private var _binding: BookmarkScreenBinding? = null
    private val binding get() = _binding!!
    private val model: BookmarkViewModel by viewModel()

    private val animeAdapter = CategoriesPageAdapter(isDetail = true)
    private val characterAdapter = CharactersPageAdapter()
    private lateinit var tabAdapter: TabAdapter

    private var isAnimeSelected = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BookmarkScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabAdapter = TabAdapter { isAnime ->
            isAnimeSelected = isAnime
            updateMainAdapter()
        }

        val concatAdapter = ConcatAdapter(tabAdapter, animeAdapter) // default: anime first
        binding.bookmarkRv.adapter = concatAdapter
        binding.bookmarkRv.setupGridLayoutForBookmarks()

        animeAdapter.setClickDetail { openPlayer(it.id) }
        characterAdapter.setClickListener { openPlayerCharacter(it.id) }

        model.getAllBookmarks()
        model.getAllCharacterBookmarks()

        model.bookmarkData.observe(viewLifecycleOwner) { list ->
            animeAdapter.updateCategoriesAll(list.map { it.toDomain() } as ArrayList<MainModel>)
        }

        model.characterData.observe(viewLifecycleOwner) { characters ->
            characterAdapter.updateCharacters(characters)
        }
    }

    private fun openPlayerCharacter(id: Int) {

    }

    private fun updateMainAdapter() {
        val concatAdapter = if (isAnimeSelected)
            ConcatAdapter(tabAdapter, animeAdapter)
        else
            ConcatAdapter(tabAdapter, characterAdapter)

        binding.bookmarkRv.adapter = concatAdapter
        binding.bookmarkRv.setupGridLayoutForBookmarks()
    }

    private fun openPlayer(id: Int) {
        val intent = Intent(requireActivity(), PlayerActivity::class.java)
        intent.putExtra("model", id)
        requireActivity().startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
