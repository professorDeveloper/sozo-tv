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
    private var currentSelectedAnimeSource = ""
    private var currentSelectedMovieSource = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SourceScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentSelectedAnimeSource = preferenceManager.getString(LocalData.SOURCE)
        currentSelectedMovieSource = preferenceManager.getString(LocalData.MOVIE_SOURCE)

        Log.d("SourceScreen", "Loaded Anime Source: $currentSelectedAnimeSource")
        Log.d("SourceScreen", "Loaded Movie Source: $currentSelectedMovieSource")

        initializeAdapter()
        setupRecyclerView()
        fetchSources()
    }

    private fun initializeAdapter() {
        adapter = GroupedSourceAdapter(
            onSourceClick = { sub ->
                when (sub.sourceType) {
                    "anime" -> {
                        saveData(LocalData.SOURCE, sub.sourceId)
                        currentSelectedAnimeSource = sub.sourceId
                        Log.d("SourceScreen", "Saved Anime Source: ${sub.sourceId}")
                        updateDisplayText()
                    }

                    "movie" -> {
                        saveData(LocalData.MOVIE_SOURCE, sub.sourceId)
                        currentSelectedMovieSource = sub.sourceId
                        Log.d("SourceScreen", "Saved Movie Source: ${sub.sourceId}")
                        updateDisplayText()
                    }
                }
            },
            selectedAnimeId = currentSelectedAnimeSource,
            selectedMovieId = currentSelectedMovieSource
        )
    }

    private fun setupRecyclerView() {
        binding.sourceRv.adapter = adapter
    }

    private fun fetchSources() {
        showLoading()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val sources = fetchSourcesFromFirebase()
                if (isAdded && view != null) { // Check if fragment is still attached
                    showSources(sources)
                    updateDisplayText()
                }
            } catch (e: Exception) {
                Log.e("SourceScreen", "Error fetching sources", e)
                if (isAdded && view != null) { // Check if fragment is still attached
                    showErrorState()
                }
            }
        }
    }

    private suspend fun fetchSourcesFromFirebase(): List<SubSource> {
        val dbRef = FirebaseDatabase.getInstance().getReference("sources")
        val snapshot = dbRef.get().await()
        return snapshot.children.mapNotNull { it.getValue(SubSource::class.java) }
    }

    private fun saveData(key: String, value: String) {
        preferenceManager.putString(key, value)
        Log.d("SharedPrefs", "Saved: key=$key, value=$value")
    }

    @SuppressLint("SetTextI18n")
    private fun showSources(list: List<SubSource>) {
        if (!isAdded || view == null) return // Check if fragment is still attached

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
        if (!isAdded || view == null) return // Check if fragment is still attached

        val animeSource = currentSelectedAnimeSource
        val movieSource = currentSelectedMovieSource

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
        if (!isAdded || view == null) return // Check if fragment is still attached

        binding.progressBar.visibility = View.VISIBLE
        binding.sourcePlaceHolder.root.visibility = View.GONE
        binding.sourceRv.visibility = View.VISIBLE
    }

    private fun showErrorState() {
        if (!isAdded || view == null) return // Check if fragment is still attached

        binding.progressBar.visibility = View.GONE
        binding.sourcePlaceHolder.root.visibility = View.VISIBLE
        binding.sourceRv.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear the binding reference to avoid memory leaks
        _binding = null
    }
}