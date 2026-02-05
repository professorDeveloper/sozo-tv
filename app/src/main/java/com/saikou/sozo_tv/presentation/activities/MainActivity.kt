package com.saikou.sozo_tv.presentation.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.NavHostFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.components.navigation.setupWithNavController
import com.saikou.sozo_tv.data.local.pref.AuthPrefKeys
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
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
    private val preferenceManager by lazy { PreferenceManager() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        model.loadProfile()
        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navMainFragment.id) as NavHostFragment
        val navController = navHostFragment.navController

        binding.navMain.setupWithNavController(navController)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.navMainFragment.isFocusedByDefault = true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d("Navigation", "Destination changed: ${destination.id}")

            binding.navMain.headerView?.apply {
                val header = ContentHeaderMenuMainTvBinding.bind(this)
                headerBinding = header
                handleUserDataState(header)
                header.root.setOnClickListener {
                    navigateProfile()
                }

                setOnOpenListener {
                    header.headerContainer.visibility = View.VISIBLE
                }
                setOnCloseListener {
                    header.headerContainer.visibility = View.GONE
                }
            }

            when (destination.id) {
                R.id.search, R.id.home, R.id.categories, R.id.contact, R.id.tvgarden, R.id.myList ->
                    binding.navMain.visibility = View.VISIBLE

                else -> binding.navMain.visibility = View.GONE
            }
        }

        updateMyListMenuVisibility()
    }

    private fun handleUserDataState(header: ContentHeaderMenuMainTvBinding) {
        model.profileData.observe(this) { profile ->
            header.tvNavigationHeaderTitle.text = profile.name
            ImageViewCompat.setImageTintList(header.ivNavigationHeaderIcon, null)
            header.ivNavigationHeaderIcon.loadImage(profile.avatarUrl)
        }
    }

    private fun updateMyListMenuVisibility() {
        val isLoggedIn = preferenceManager.getString(AuthPrefKeys.ANILIST_TOKEN).isNotEmpty()
        val menu = binding.navMain.menu
        val myListMenuItem = menu.findItem(R.id.myList)

        myListMenuItem?.isVisible = isLoggedIn

        Log.d(
            "MainActivity",
            "User logged in: $isLoggedIn, My List visible: ${myListMenuItem?.isVisible}"
        )
    }

    private fun navigateProfile() {
        val intent = Intent(this@MainActivity, ProfileActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun navigateToCategory(it: String) {
        LocalData.currentCategory = it
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navMainFragment.id) as NavHostFragment
        val navController = navHostFragment.navController
        navController.navigate(R.id.categories)
    }

    override fun onResume() {
        super.onResume()
        updateMyListMenuVisibility()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        headerBinding = null
    }
}