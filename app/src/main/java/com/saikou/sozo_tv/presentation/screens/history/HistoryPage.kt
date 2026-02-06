package com.saikou.sozo_tv.presentation.screens.history

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.saikou.sozo_tv.adapters.HistoryAdapter
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.HistoryPageBinding
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.viewmodel.PlayAnimeViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.isAnimeEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel


class HistoryPage : Fragment() {
    private lateinit var binding: HistoryPageBinding
    private val model by viewModel<PlayAnimeViewModel>()
    private val historyAdapter = HistoryAdapter()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = HistoryPageBinding.inflate(
            inflater, container, false
        )

        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            val watchHistoryList = if (isAnimeEnabled) model.getAllWatchHistory()
                .filter { it.isAnime }
                .filter {
                    it.source == PreferenceManager().getString(LocalData.SOURCE)
                } else model.getAllWatchHistory()
                .filter { !it.isAnime }
            if (watchHistoryList.isNotEmpty()) {
                binding.historyGroup.visibility = View.VISIBLE
                binding.placeHolder.root.visibility = View.GONE
                historyAdapter.submitList(watchHistoryList)
                binding.historyRv.adapter = historyAdapter
                historyAdapter.setItemHistoryListener {
                    if (it.isEpisode) {
                        if (isAnimeEnabled) {
                            val intent = Intent(binding.root.context, PlayerActivity::class.java)
                            intent.putExtra("session", it.session)
                            intent.putExtra("page", it.page)
                            intent.putExtra("epIndex", it.epIndex)
                            intent.putExtra("mediaId", it.categoryid)
                            intent.putExtra("image", it.image)
                            intent.putExtra("animeTitle", it.mediaName)
                            intent.putExtra("isHistory", true)
                            intent.putExtra("isSeries", it.isSeries)
                            intent.putExtra("isAnime", it.isAnime)
//                        LocalData.itemMovieWatch = it
//                        LocalData.isSeries = true
                            requireContext().startActivity(intent)
                            binding.root.context.startActivity(intent)
                        } else {
                            val intent = Intent(binding.root.context, PlayerActivity::class.java)
                            intent.putExtra("session", it.session)
                            intent.putExtra("page", it.page)
                            intent.putExtra("epIndex", it.epIndex)
                            intent.putExtra("mediaId", it.categoryid)
                            intent.putExtra("imdb", it.imdbID)
                            intent.putExtra("image", it.image)
                            intent.putExtra("animeTitle", it.mediaName)
                            intent.putExtra("isHistory", true)
                            intent.putExtra("currentSource", it.currentSourceName)
                            intent.putExtra("isSeries", it.isSeries)
                            requireContext().startActivity(intent)
                            binding.root.context.startActivity(intent)
                        }
                    }
                }

            } else {
                binding.placeHolder.root.visibility = View.VISIBLE
                binding.historyGroup.visibility = View.GONE
            }
        }
        binding.clearHistoryBtn.setOnClickListener {
            val dialog = HistoryAlertDialog()
            dialog.setNoClearListener {
                dialog.dismiss()
                clearHistory()
            }
            dialog.setYesContinueListener {
                dialog.dismiss()
            }
            dialog.show(parentFragmentManager, "ConfirmationDialog")
        }
    }

    private fun clearHistory() {
        lifecycleScope.launch {
            model.clearAllHistory()
            val watchHistoryList = withContext(Dispatchers.IO) {
                model.getAllWatchHistory()
            }
            if (watchHistoryList.isNotEmpty()) {
                binding.historyGroup.visibility = View.VISIBLE
                binding.placeHolder.root.visibility = View.GONE
                historyAdapter.submitList(watchHistoryList)
            } else {
                binding.placeHolder.root.visibility = View.VISIBLE
                binding.historyGroup.visibility = View.GONE
            }
        }
    }
}