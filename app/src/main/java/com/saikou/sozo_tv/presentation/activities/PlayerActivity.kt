package com.saikou.sozo_tv.presentation.activities

import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ActivityPlayerBinding
import com.saikou.sozo_tv.presentation.screens.detail.CastDetailScreenArgs
import com.saikou.sozo_tv.presentation.screens.episodes.EpisodeScreenArgs
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import com.saikou.sozo_tv.utils.LocalData
import org.koin.androidx.viewmodel.ext.android.viewModel

class PlayerActivity : AppCompatActivity() {
    private val playerViewModel: PlayViewModel by viewModel()
    private lateinit var binding: ActivityPlayerBinding
    private var categoryDetails: Int = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navController =
            (supportFragmentManager.findFragmentById(R.id.nav_main_fragment) as NavHostFragment).navController
        categoryDetails = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getIntExtra("model", -1)
        } else {
            intent.getIntExtra("model", -1)
        }

        val isHistory = intent.getBooleanExtra("isHistory", false)

        if (isHistory) {
            val session = intent.getStringExtra("session")
            val mediaId = intent.getStringExtra("mediaId")
            val animeTitle = intent.getStringExtra("animeTitle")
            val page = intent.getIntExtra("page",-1)
            val epIndex = intent.getIntExtra("epIndex",-1)
            val navInflater = navController.navInflater
            LocalData.bookmark
            val graph = navInflater.inflate(R.navigation.play_graph)
            graph.setStartDestination(R.id.episodeScreen)
            navController.setGraph(
                graph,
                startDestinationArgs = EpisodeScreenArgs(
                    mediaId = page,
                    episodeTitle ="${session}/${mediaId}/${animeTitle}/${epIndex}",
                    isFree = false
                ).toBundle()
            )
        }else {
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
                playerViewModel.loadAnimeById(id = categoryDetails)
                playerViewModel.loadCast(id = categoryDetails)
                playerViewModel.loadRelations(id = categoryDetails)
                playerViewModel.checkBookmark(id = categoryDetails)
            }
        }




    }
}