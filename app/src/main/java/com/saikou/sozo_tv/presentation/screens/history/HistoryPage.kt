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
import com.saikou.sozo_tv.databinding.HistoryPageBinding
import com.saikou.sozo_tv.presentation.activities.PlayerActivity
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import com.saikou.sozo_tv.utils.LocalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.viewModel


class HistoryPage : Fragment() {
    private lateinit var binding: HistoryPageBinding
    private val model by viewModel<PlayViewModel>()
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
//        LocalData.itemMovieWatch = null
        lifecycleScope.launch {
            val watchHistoryList = model.getAllWatchHistory()
            if (watchHistoryList.isNotEmpty()) {
                binding.historyGroup.visibility = View.VISIBLE
                binding.placeHolder.root.visibility = View.GONE
                historyAdapter.submitList(watchHistoryList)
                binding.historyRv.adapter = historyAdapter
                historyAdapter.setItemHistoryListener {
                    if (it.isEpisode) {
                        val intent = Intent(binding.root.context, PlayerActivity::class.java)
                        intent.putExtra("model", it.session)
                        intent.putExtra("isHistory", true)
//                        LocalData.itemMovieWatch = it
//                        LocalData.isSeries = true
                        requireContext().startActivity(intent)
                        binding.root.context.startActivity(intent)
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
                lifecycleScope.launch {
                    val watchHistoryList = withContext(Dispatchers.IO) {
                        model.clearAllHistory()
                        model.getAllWatchHistory()
                    }
                    dialog.dismiss()
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
            dialog.setYesContinueListener {
                dialog.dismiss()
            }
            dialog.show(parentFragmentManager, "ConfirmationDialog")
        }
    }
}