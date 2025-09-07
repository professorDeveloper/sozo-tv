package com.saikou.sozo_tv.presentation.screens.bookmark

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.adapters.CharactersPageAdapter
import com.saikou.sozo_tv.databinding.BookmarkScreenBinding
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import com.saikou.sozo_tv.presentation.viewmodel.BookmarkViewModel
import com.saikou.sozo_tv.utils.toDomain
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.saikou.sozo_tv.R

class BookmarkScreen : Fragment() {
    private var _binding: BookmarkScreenBinding? = null
    private val binding get() = _binding!!
    private val model: BookmarkViewModel by viewModel()

    private val animeAdapter = CategoriesPageAdapter(isDetail = true)
    private val characterAdapter = CharactersPageAdapter()

    private var isAnimeSelected = true
    private var lastScrollY = 0

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

        setupVerticalGridView()

        binding.bookmarkRv.adapter = animeAdapter
        updateTabSelection()

        animeAdapter.setClickDetail { openPlayer(it.id) }
        characterAdapter.setClickListener { openPlayerCharacter(it.id) }

        if (isAnimeSelected) {
            model.getAllBookmarks()
        } else {
            model.getAllCharacterBookmarks()
        }

        model.bookmarkData.observe(viewLifecycleOwner) { list ->
            val domainList = list.map { it.toDomain() } as ArrayList<MainModel>
            animeAdapter.updateCategoriesAll(domainList)
            binding.bookmarkPlaceHolder.root.visibility =
                if (domainList.isEmpty() && isAnimeSelected) View.VISIBLE else View.GONE
            binding.bookmarkRv.visibility =
                if (domainList.isEmpty() && isAnimeSelected) View.GONE else View.VISIBLE
        }

        model.characterData.observe(viewLifecycleOwner) { characters ->
            characterAdapter.updateCharacters(characters)
            binding.bookmarkPlaceHolder.root.visibility =
                if (characters.isEmpty() && !isAnimeSelected) View.VISIBLE else View.GONE
            binding.bookmarkRv.visibility =
                if (characters.isEmpty() && !isAnimeSelected) View.GONE else View.VISIBLE
        }

        binding.topBar.navAnime.setOnClickListener {
            isAnimeSelected = true
            binding.bookmarkRv.adapter = animeAdapter
            model.getAllBookmarks()
            updateTabSelection()
            showTopBar()
        }

        binding.topBar.navCharacters.setOnClickListener {
            isAnimeSelected = false
            binding.bookmarkRv.adapter = characterAdapter
            model.getAllCharacterBookmarks()
            updateTabSelection()
            showTopBar()
        }

        animeAdapter.setCategoriesPageInterface(object : CategoriesPageAdapter.CategoriesPageInterface {
            override fun onCategorySelected(category: MainModel, position: Int) {
            }
        })
    }

    private fun setupVerticalGridView() {
        binding.bookmarkRv.setNumColumns(4)
        binding.bookmarkRv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var isTopBarHidden = false

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = binding.bookmarkRv.layoutManager as? GridLayoutManager
                val firstVisibleItemPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
                val isScrollingDown = dy > 0 && dy > lastScrollY
                lastScrollY = dy

                if (isScrollingDown && !isTopBarHidden && firstVisibleItemPosition > 0) {
                    isTopBarHidden = true
                    hideTopBar()
                } else if ((firstVisibleItemPosition == 0 || dy <= 0) && isTopBarHidden) {
                    isTopBarHidden = false
                    showTopBar()
                }
            }
        })
    }

    private fun updateTabSelection() {
        binding.topBar.navAnime.setBackgroundResource(
            if (isAnimeSelected) R.drawable.tab_background_selector else R.drawable.tab_background_unselected
        )
        binding.topBar.navCharacters.setBackgroundResource(
            if (!isAnimeSelected) R.drawable.tab_background_selector else R.drawable.tab_background_unselected
        )
    }

    private fun hideTopBar() {
        binding.topBar.root.animate()
            .translationY(-binding.topBar.root.height.toFloat())
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .start()
    }

    private fun showTopBar() {
        binding.topBar.root.animate()
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .start()
    }

    private fun openPlayerCharacter(id: Int) {
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