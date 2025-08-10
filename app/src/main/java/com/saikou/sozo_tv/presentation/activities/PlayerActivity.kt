package com.saikou.sozo_tv.presentation.activities

import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ActivityPlayerBinding
import com.saikou.sozo_tv.domain.model.CategoryDetails
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
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


        playerViewModel.loadAnimeById(id = categoryDetails)
        playerViewModel.loadRelations(id = categoryDetails)
    }
}