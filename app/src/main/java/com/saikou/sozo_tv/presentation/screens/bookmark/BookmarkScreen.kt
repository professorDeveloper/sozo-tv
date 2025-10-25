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
import com.saikou.sozo_tv.data.model.BookmarkType
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.isAnimeEnabled
import com.saikou.sozo_tv.utils.LocalData.isBookmarkClicked

class BookmarkScreen : Fragment() {
    private var _binding: BookmarkScreenBinding? = null
    private val binding get() = _binding!!
    private val model: BookmarkViewModel by viewModel()

    private val animeAdapter = CategoriesPageAdapter(isDetail = true)
    private val characterAdapter = CharactersPageAdapter()

    private var bookmarkType = BookmarkType.MEDIA
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
        model.getAllCharacterBookmarks()
        model.getAllBookmarks()
        setupVerticalGridView()

        binding.bookmarkRv.adapter = animeAdapter
        updateTabSelection()

        animeAdapter.setClickDetail { openPlayer(it.id) }
        characterAdapter.setClickListener { openPlayerCharacter(it.id) }


        model.bookmarkData.observe(viewLifecycleOwner) { list ->
            if (LocalData.isAnimeEnabled) {
                val domainList =
                    list.map { it.toDomain() }.filter { it.isAnime } as ArrayList<MainModel>
                animeAdapter.updateCategoriesAll(domainList)
                binding.bookmarkPlaceHolder.root.visibility =
                    if (domainList.isEmpty() && bookmarkType == BookmarkType.MEDIA) View.VISIBLE else View.GONE
                binding.bookmarkRv.visibility =
                    if (domainList.isEmpty() && bookmarkType == BookmarkType.MEDIA) View.GONE else View.VISIBLE
            } else {
                val domainList =
                    list.map { it.toDomain() }.filter { !it.isAnime } as ArrayList<MainModel>
                animeAdapter.updateCategoriesAll(domainList)
                binding.bookmarkPlaceHolder.root.visibility =
                    if (domainList.isEmpty() && bookmarkType == BookmarkType.MEDIA) View.VISIBLE else View.GONE
                binding.bookmarkRv.visibility =
                    if (domainList.isEmpty() && bookmarkType == BookmarkType.MEDIA) View.GONE else View.VISIBLE
            }
        }

        model.characterData.observe(viewLifecycleOwner) { characters ->
            characterAdapter.updateCharacters(characters)
            binding.bookmarkPlaceHolder.root.visibility =
                if (characters.isEmpty() && bookmarkType != BookmarkType.MEDIA) View.VISIBLE else View.GONE
            binding.bookmarkRv.visibility =
                if (characters.isEmpty() && bookmarkType != BookmarkType.MEDIA) View.GONE else View.VISIBLE
        }

        binding.topBar.navAnime.setOnClickListener {
            bookmarkType = BookmarkType.MEDIA
            binding.bookmarkRv.adapter = animeAdapter
            model.getAllBookmarks()
            updateTabSelection()
            showTopBar()
        }

        binding.topBar.movieTxt.text = if (isAnimeEnabled) "Anime" else "Movie"
        binding.topBar.navCharacters.setOnClickListener {
            bookmarkType = BookmarkType.CHARACTER
            binding.bookmarkRv.adapter = characterAdapter
            model.getAllCharacterBookmarks()
            updateTabSelection()
            showTopBar()
        }
        binding.topBar.navChannels.setOnClickListener {
            bookmarkType = BookmarkType.TV_CHANNEL

            updateTabSelection()
            showTopBar()
        }

        animeAdapter.setCategoriesPageInterface(object :
            CategoriesPageAdapter.CategoriesPageInterface {
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
                val firstVisibleItemPosition =
                    layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
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
        when (bookmarkType) {
            BookmarkType.MEDIA -> {
                binding.topBar.navAnime.setBackgroundResource(R.drawable.tab_background_selector)
                binding.topBar.navCharacters.setBackgroundResource(R.drawable.tab_background_unselected)
                binding.topBar.navChannels.setBackgroundResource(R.drawable.tab_background_unselected)
            }

            BookmarkType.CHARACTER -> {
                binding.topBar.navAnime.setBackgroundResource(R.drawable.tab_background_unselected)
                binding.topBar.navCharacters.setBackgroundResource(R.drawable.tab_background_selector)
                binding.topBar.navChannels.setBackgroundResource(R.drawable.tab_background_unselected)
            }

            BookmarkType.TV_CHANNEL -> {
                binding.topBar.navAnime.setBackgroundResource(R.drawable.tab_background_unselected)
                binding.topBar.navCharacters.setBackgroundResource(R.drawable.tab_background_unselected)
                binding.topBar.navChannels.setBackgroundResource(R.drawable.tab_background_selector)
            }
        }

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
        val intent = Intent(requireActivity(), PlayerActivity::class.java)
        intent.putExtra("character", id)
        requireActivity().startActivity(intent)
        isBookmarkClicked = true
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