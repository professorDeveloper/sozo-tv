package com.saikou.sozo_tv.presentation.screens.bookmark

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.ChannelsAdapter
import com.saikou.sozo_tv.adapters.CharactersPageAdapter
import com.saikou.sozo_tv.data.model.BookmarkType
import com.saikou.sozo_tv.data.model.Channel
import com.saikou.sozo_tv.databinding.BookmarkScreenBinding
import com.saikou.sozo_tv.domain.model.MainModel
import com.saikou.sozo_tv.presentation.activities.LiveTvActivity
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import com.saikou.sozo_tv.presentation.viewmodel.BookmarkViewModel
import com.saikou.sozo_tv.utils.LocalData.isAnimeEnabled
import com.saikou.sozo_tv.utils.LocalData.isBookmarkClicked
import com.saikou.sozo_tv.data.model.toDomain
import com.saikou.sozo_tv.utils.visible
import org.koin.androidx.viewmodel.ext.android.viewModel

class BookmarkScreen : Fragment() {
    private var _binding: BookmarkScreenBinding? = null
    private val binding get() = _binding!!
    private val model: BookmarkViewModel by viewModel()

    private val animeAdapter = CategoriesPageAdapter(isDetail = true)
    private val characterAdapter = CharactersPageAdapter()

    private var bookmarkType = BookmarkType.MEDIA

    /**
     * All three tabs share one grid and one placeholder, and all three observers
     * fire whether or not their tab is showing. Each used to set that shared
     * visibility from its own condition, so an empty Bookmarks tab had its
     * placeholder wiped the moment the character list arrived. The observers now
     * only record what they loaded; [renderTab] alone decides what is on screen.
     */
    private var mediaCount = 0
    private var characterCount = 0
    private var channelCount = 0
    private val channelsAdapter by lazy {
        ChannelsAdapter {
            val intent = Intent(requireContext(), LiveTvActivity::class.java)
            intent.putExtra("url", it.iptvUrls[0])
            intent.putExtra("title", it.name)
            intent.putExtra("data", it)
            requireActivity().startActivity(intent)
        }
    }

    private fun renderTab() {
        val b = _binding ?: return
        val count = when (bookmarkType) {
            BookmarkType.MEDIA -> mediaCount
            BookmarkType.CHARACTER -> characterCount
            BookmarkType.TV_CHANNEL -> channelCount
        }
        b.bookmarkRv.adapter = when (bookmarkType) {
            BookmarkType.MEDIA -> animeAdapter
            BookmarkType.CHARACTER -> characterAdapter
            BookmarkType.TV_CHANNEL -> channelsAdapter
        }
        b.bookmarkRv.setNumColumns(4)
        b.bookmarkRv.visibility = if (count == 0) View.GONE else View.VISIBLE
        b.bookmarkPlaceHolder.root.visibility = if (count == 0) View.VISIBLE else View.GONE
        // Saying "no movie or serial" on the Characters tab describes the wrong list.
        b.bookmarkPlaceHolder.placeholderTxt.setText(
            when (bookmarkType) {
                BookmarkType.MEDIA -> R.string.place_holder_text
                BookmarkType.CHARACTER -> R.string.empty_no_characters
                BookmarkType.TV_CHANNEL -> R.string.empty_no_channels
            }
        )
    }
    private var lastScrollY = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BookmarkScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.getAllCharacterBookmarks()
        model.getAllBookmarks()
        setupVerticalGridView()

        updateTabSelection()
        renderTab()

        animeAdapter.setClickDetail { openPlayer(it.id) }
        characterAdapter.setClickListener { openPlayerCharacter(it.id) }


        model.bookmarkData.observe(viewLifecycleOwner) { list ->
            val domainList = list.map { it.toDomain() }
                .filter { it.isAnime == isAnimeEnabled } as ArrayList<MainModel>
            mediaCount = domainList.size
            animeAdapter.updateCategoriesAll(domainList)
            renderTab()
        }

        model.characterData.observe(viewLifecycleOwner) { characters ->
            characterCount = characters.size
            characterAdapter.updateCharacters(characters)
            renderTab()
        }

        model.channelData.observe(viewLifecycleOwner) { channels ->
            val channelList = channels.map {
                Channel(
                    it.id,
                    it.name,
                    arrayListOf(it.iptvUrl),
                    arrayListOf(),
                    it.language,
                    it.country,
                    it.isGeoBlocked
                )
            }
            channelCount = channelList.size
            channelsAdapter.updateChannels(channelList)
            renderTab()
        }

        binding.topBar.navAnime.setOnClickListener {
            bookmarkType = BookmarkType.MEDIA
            model.getAllBookmarks()
            updateTabSelection()
            renderTab()
            showTopBar()
        }

        binding.topBar.movieTxt.text = if (isAnimeEnabled) "Anime" else "Movie"
        binding.topBar.navCharacters.setOnClickListener {
            bookmarkType = BookmarkType.CHARACTER
            model.getAllCharacterBookmarks()
            updateTabSelection()
            renderTab()
            showTopBar()
        }
        binding.topBar.navChannels.setOnClickListener {
            bookmarkType = BookmarkType.TV_CHANNEL
            model.getAllChannelBookmarks()
            updateTabSelection()
            renderTab()
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
        binding.topBar.root.animate().translationY(-binding.topBar.root.height.toFloat())
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).start()
    }

    private fun showTopBar() {
        binding.topBar.root.animate().translationY(0f).setDuration(200)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator()).start()
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