package com.saikou.sozo_tv.presentation.screens.source

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.FirebaseDatabase
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.GroupedSourceAdapter
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.model.SubSource
import com.saikou.sozo_tv.databinding.SourceScreenBinding
import com.saikou.sozo_tv.domain.model.GroupedSource
import com.saikou.sozo_tv.utils.LocalData
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SourceScreen : Fragment() {
    private var _binding: SourceScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: GroupedSourceAdapter

    private val preferenceManager by lazy { PreferenceManager() }
    private val currentSelectedAnimeSource by lazy {
        val source = preferenceManager.getString(LocalData.SOURCE)
        Log.d("SourceScreen", "Loaded Anime Source: $source")
        source
    }
    private val currentSelectedMovieSource by lazy {
        val source = preferenceManager.getString(LocalData.MOVIE_SOURCE)
        Log.d("SourceScreen", "Loaded Movie Source: $source")
        source
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SourceScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun saveData(key: String, value: String) {
        val preferenceManager = PreferenceManager()
        preferenceManager.putString(key, value)
        Log.d("SharedPrefs", "Saved: key=$key, value=$value")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dbRef = FirebaseDatabase.getInstance().getReference("sources")

        adapter = GroupedSourceAdapter(
            onSourceClick = { sub ->
                when (sub.sourceType) {
                    "anime" -> {
                        saveData(LocalData.SOURCE, sub.sourceId)
                        Log.d("SourceScreen", "Saved Anime Source: ${sub.sourceId}")
                        updateDisplayText()
                    }

                    "movie" -> {
                        saveData(LocalData.MOVIE_SOURCE, sub.sourceId)
                        Log.d("SourceScreen", "Saved Movie Source: ${sub.sourceId}")
                        updateDisplayText()
                    }
                }
            },
            selectedAnimeId = currentSelectedAnimeSource,
            selectedMovieId = currentSelectedMovieSource
        )

        binding.sourceRv.adapter = adapter
        showLoading()

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                fetchSourcesOnce(dbRef)
            }.onSuccess { list ->
                showSources(list)
                updateDisplayText()
            }.onFailure {
                showErrorState()
            }
        }
    }

    private suspend fun fetchSourcesOnce(dbRef: com.google.firebase.database.DatabaseReference): List<SubSource> {
        val snapshot = dbRef.get().await()
        return snapshot.children.mapNotNull { it.getValue(SubSource::class.java) }
    }

    @SuppressLint("SetTextI18n")
    private fun showSources(list: List<SubSource>) {
        binding.progressBar.visibility = View.GONE

        if (list.isEmpty()) {
            binding.sourcePlaceHolder.root.visibility = View.VISIBLE
            binding.sourceRv.visibility = View.GONE
            return
        }

        binding.sourcePlaceHolder.root.visibility = View.GONE
        binding.sourceRv.visibility = View.VISIBLE

        val animeSources = list.filter { it.sourceType == "anime" }
        val movieSources = list.filter { it.sourceType == "movie" }

        val groupedList = mutableListOf<GroupedSource>()

        if (animeSources.isNotEmpty()) {
            groupedList.add(GroupedSource("anime", "Anime Sources", animeSources))
        }

        if (movieSources.isNotEmpty()) {
            groupedList.add(GroupedSource("movie", "Movie Sources", movieSources))
        }

        adapter.updateList(groupedList)
    }

    private fun updateDisplayText() {
        val animeSource = preferenceManager.getString(LocalData.SOURCE)
        val movieSource = preferenceManager.getString(LocalData.MOVIE_SOURCE)

        val displayText = buildString {
            if (animeSource.isNotEmpty()) {
                append("Anime: $animeSource")
            }
            if (movieSource.isNotEmpty()) {
                if (isNotEmpty()) append(", ")
                append("Movie: $movieSource")
            }
            if (isEmpty()) {
                append(getString(R.string.sources))
            }
        }

        binding.textView6.text = displayText
        Log.d("SourceScreen", "Display Text: $displayText")
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.sourcePlaceHolder.root.visibility = View.GONE
        binding.sourceRv.visibility = View.VISIBLE
    }

    private fun showErrorState() {
        binding.progressBar.visibility = View.GONE
        binding.sourcePlaceHolder.root.visibility = View.VISIBLE
        binding.sourceRv.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}