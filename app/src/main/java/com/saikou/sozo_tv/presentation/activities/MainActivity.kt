package com.saikou.sozo_tv.presentation.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.saikou.sozo_tv.presentation.screens.search.SearchScreen
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.ImageViewCompat
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.components.navigation.setupWithNavController
import com.saikou.sozo_tv.databinding.ActivityMainBinding
import com.saikou.sozo_tv.databinding.ContentHeaderMenuMainTvBinding
import com.saikou.sozo_tv.presentation.viewmodel.SettingsViewModel
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.finishDeferred
import com.saikou.sozo_tv.utils.loadImage
import org.koin.androidx.viewmodel.ext.android.viewModel

// AppCompatActivity (not FragmentActivity): CloudStream plugins cast the context handed to
// `Plugin.load()` to AppCompatActivity, and this is the screen in the foreground while the
// extension engine loads them. `Theme.Tv` descends from Theme.MaterialComponents, so it qualifies.
class MainActivity : AppCompatActivity() {
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
        setupBackBehaviour()
    }

    /**
     * BACK walks out to the navigation rail before it leaves the app.
     *
     * There was no callback at all, so the default finished the activity: a single BACK
     * anywhere on Home killed the app, skipping the rail entirely. `navMainFragment` is
     * `isFocusedByDefault` (see setupNavigation), so focus always starts in the content
     * pane — the rail was only reachable by pressing LEFT, and never by the gesture users
     * actually reach for.
     */
    private fun setupBackBehaviour() {
        onBackPressedDispatcher.addCallback(this) {
            val rail = binding.navMain
            if (rail.isVisible && !rail.hasFocus()) {
                rail.requestFocus()
                return@addCallback
            }
            // Already on the rail (or it is hidden on this destination) — fall through to
            // the platform default, which finishes the activity.
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navMainFragment.id) as NavHostFragment
        val navController = navHostFragment.navController

        binding.navMain.setupWithNavController(navController)

        // A query handed over from another activity (the AniList screen's "find in
        // sources"). Consumed rather than read: this activity is often reached
        // through singleTop, and leaving the extra on the intent would re-open
        // search on every later return to Home.
        intent?.getStringExtra(EXTRA_SEARCH_QUERY)?.takeIf { it.isNotBlank() }?.let { query ->
            intent.removeExtra(EXTRA_SEARCH_QUERY)
            navController.navigate(
                R.id.search,
                Bundle().apply { putString(SearchScreen.ARG_QUERY, query) },
            )
        }

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
                R.id.search, R.id.home, R.id.categories, R.id.contact, R.id.tvgarden ->
                    binding.navMain.visibility = View.VISIBLE

                else -> binding.navMain.visibility = View.GONE
            }
        }
    }

    private fun handleUserDataState(header: ContentHeaderMenuMainTvBinding) {
        model.profileData.observe(this) { profile ->
            header.tvNavigationHeaderTitle.text = profile.name
            // A guest has no avatar, and loadImage() falls back to the 404 wallpaper for a blank
            // URL — leave the tinted placeholder icon in place instead.
            if (profile.avatarUrl.isNullOrBlank()) return@observe
            ImageViewCompat.setImageTintList(header.ivNavigationHeaderIcon, null)
            header.ivNavigationHeaderIcon.loadImage(profile.avatarUrl)
        }
    }

    private fun navigateProfile() {
        val intent = Intent(this@MainActivity, ProfileActivity::class.java)
        startActivity(intent)
        finishDeferred()
    }

    fun navigateToCategory(it: String) {
        LocalData.currentCategory = it
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navMainFragment.id) as NavHostFragment
        val navController = navHostFragment.navController
        navController.navigate(R.id.categories)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        headerBinding = null
    }

    companion object {
        /** Intent extra carrying a search query from another activity. */
        const val EXTRA_SEARCH_QUERY = "sozo.extra.SEARCH_QUERY"
    }

}