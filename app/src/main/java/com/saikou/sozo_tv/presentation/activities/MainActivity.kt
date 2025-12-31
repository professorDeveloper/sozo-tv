package com.saikou.sozo_tv.presentation.activities


import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.NavHostFragment
import com.saikou.sozo_tv.components.navigation.setupWithNavController
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ActivityMainBinding
import com.saikou.sozo_tv.databinding.ContentHeaderMenuMainTvBinding
import com.saikou.sozo_tv.presentation.viewmodel.SettingsViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.loadImage
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : FragmentActivity() {
    private val model: SettingsViewModel by viewModel()
    private var _binding: ActivityMainBinding? = null
    private var headerBinding: ContentHeaderMenuMainTvBinding? = null
    private val binding get() = _binding!!
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        model.loadProfile()
        setupNavigation()
    }

    private fun handleUserDataState(header: ContentHeaderMenuMainTvBinding) {
        model.profileData.observe(this) {
            header.tvNavigationHeaderTitle.text = it.name
            ImageViewCompat.setImageTintList(header.ivNavigationHeaderIcon, null)
            header.ivNavigationHeaderIcon.loadImage(it.avatarUrl)
        }
    }


    @SuppressLint("ObsoleteSdkInt")
    private fun setupNavigation() {
        val navHostFragment =
            this.supportFragmentManager.findFragmentById(binding.navMainFragment.id) as NavHostFragment
        val navController = navHostFragment.navController

        binding.navMain.setupWithNavController(navController)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.navMainFragment.isFocusedByDefault = true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d("AAAA", "setupNavigation: state ")
            binding.navMain.headerView?.apply {
                val header = ContentHeaderMenuMainTvBinding.bind(this)
                headerBinding = header
                handleUserDataState(header)
                header.root.setOnClickListener {
                    navigateProfile()
                }
                header.ivNavigationHeaderIcon

                setOnOpenListener {
                    header.headerContainer.visibility = View.VISIBLE
                }
                setOnCloseListener {
                    header.headerContainer.visibility = View.GONE
                }

            }

            when (destination.id) {
                R.id.search, R.id.home, R.id.categories, R.id.contact, R.id.tvgarden -> binding.navMain.visibility =
                    View.VISIBLE

                else -> binding.navMain.visibility = View.GONE
            }
        }
    }

    private fun navigateProfile() {
        val intent = Intent(this@MainActivity, ProfileActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun navigateToCategory(it: String) {
        LocalData.currentCategory = it
        val navHostFragment =
            this.supportFragmentManager.findFragmentById(binding.navMainFragment.id) as NavHostFragment
        val navController = navHostFragment.navController
        navController.navigate(R.id.categories)
    }
}
