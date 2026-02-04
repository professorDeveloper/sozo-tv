package com.saikou.sozo_tv.presentation.activities

import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.databinding.ActivityPlayerBinding
import com.saikou.sozo_tv.presentation.screens.detail.CastDetailScreenArgs
import com.saikou.sozo_tv.presentation.screens.play.MovieSeriesPlayerScreenArgs
import com.saikou.sozo_tv.presentation.screens.play.SeriesPlayerScreenArgs
import com.saikou.sozo_tv.presentation.viewmodel.DetailViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.isAnimeEnabled
import com.saikou.sozo_tv.utils.LocalData.isHistoryItemClicked
import org.koin.androidx.viewmodel.ext.android.viewModel

class PlayerActivity : AppCompatActivity() {
    private val detailViewModel: DetailViewModel by viewModel()
    private lateinit var binding: ActivityPlayerBinding
    private var categoryDetails: Int = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val navController =
            (supportFragmentManager.findFragmentById(R.id.nav_main_fragment) as NavHostFragment).navController
        categoryDetails = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getIntExtra("model", -1)
        } else {
            intent.getIntExtra("model", -1)
        }

        val isHistory = intent.getBooleanExtra("isHistory", false)

        if (isHistory) {
            if (isAnimeEnabled) {
                val session = intent.getStringExtra("session")
                val mediaId = intent.getStringExtra("mediaId")
                val animeTitle = intent.getStringExtra("animeTitle")
                val image = intent.getStringExtra("image")
                val page = intent.getIntExtra("page", -1)
                val epIndex = intent.getIntExtra("epIndex", -1)
                val navInflater = navController.navInflater
                isHistoryItemClicked = true
                val graph = navInflater.inflate(R.navigation.play_graph)
                graph.setStartDestination(R.id.seriesPlayerScreen)
                navController.setGraph(
                    graph,
                    startDestinationArgs = SeriesPlayerScreenArgs(
                        idMal = -1,
                        id = session ?: "",
                        currentPage = page,
                        currentIndex = epIndex,
                        name = animeTitle ?: "",
                        image = image ?: "",
                        seriesMainId = mediaId ?: "",
                        currentEpisode = (epIndex + 1).toString()

                    ).toBundle()
                )
            } else {
                val session = intent.getStringExtra("session")
                val mediaId = intent.getStringExtra("mediaId")
                val imdbID = intent.getStringExtra("imdb")
                val animeTitle = intent.getStringExtra("animeTitle")
                val image = intent.getStringExtra("image")
                val page = intent.getIntExtra("page", -1)
                val epIndex = intent.getIntExtra("epIndex", -1)
                val isSeries = intent.getBooleanExtra("isSeries", false)
                val navInflater = navController.navInflater
                val currentSource = intent.getStringExtra("currentSource")
                isHistoryItemClicked = true
                val graph = navInflater.inflate(R.navigation.play_graph)
                graph.setStartDestination(R.id.movieSeriesPlayerScreen)
                navController.setGraph(
                    graph,
                    startDestinationArgs = MovieSeriesPlayerScreenArgs(
                        mediaId?.toInt()!!,
                        !isSeries,
                        imdbID ?: "",
                        currentPage = page,
                        currentIndex = epIndex,
                        animeTitle ?: "",
                        image ?: "",
                        session ?: "",
                        epIndex + 1,
                        currentSource ?: ""
                    ).toBundle()
                )

            }
        } else {
            val character = intent.getIntExtra("character", -1)
            if (character != -1) {
                val navInflater = navController.navInflater
                val graph = navInflater.inflate(R.navigation.play_graph)
                graph.setStartDestination(R.id.castDetailScreen)
                navController.setGraph(
                    graph,
                    startDestinationArgs = CastDetailScreenArgs(character).toBundle()
                )
            } else {
                val preference = PreferenceManager()
                if (preference.isModeAnimeEnabled()) {
                    detailViewModel.loadAnimeById(id = categoryDetails)
                    detailViewModel.loadCast(id = categoryDetails)
                    detailViewModel.loadRelations(id = categoryDetails)
                    detailViewModel.checkBookmark(id = categoryDetails)
                } else {
                    val id = categoryDetails
                    val isMovie = intent.getBooleanExtra("isMovie", false)
                    detailViewModel.loadRelationsMovieOrSeries(id, isMovie)
                    if (isMovie) {
                        detailViewModel.loadCastSeriesOrMovie(id, isMovie)
                        detailViewModel.loadMovieById(id = id)

                    } else {
                        detailViewModel.loadSeriesById(id = id)
                        detailViewModel.loadCastSeriesOrMovie(id, isMovie)
                    }
                }
            }
        }


    }
}