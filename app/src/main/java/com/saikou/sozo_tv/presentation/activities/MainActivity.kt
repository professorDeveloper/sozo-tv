package com.saikou.sozo_tv.presentation.activities


import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.NavHostFragment
import com.ipsat.ipsat_tv.components.navigation.setupWithNavController
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.ActivityMainBinding
import com.saikou.sozo_tv.databinding.ContentHeaderMenuMainTvBinding
import com.saikou.sozo_tv.domain.preference.EncryptedPreferencesManager
import org.koin.android.ext.android.inject

class MainActivity : FragmentActivity() {


    private var _binding: ActivityMainBinding? = null
    private var headerBinding: ContentHeaderMenuMainTvBinding? = null
    private val encryptedPreferencesManager: EncryptedPreferencesManager by inject()
    private val binding get() = _binding!!
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()


    }

    private fun handleUserDataState() {
        headerBinding?.tvNavigationHeaderSubtitle?.text = encryptedPreferencesManager.getSubCode()
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
                handleUserDataState()
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
                R.id.search, R.id.home, R.id.categories, R.id.tv_shows -> binding.navMain.visibility =
                    View.VISIBLE

                else -> binding.navMain.visibility = View.GONE
            }
        }
    }

    private fun navigateProfile() {
//        val intent = Intent(this@MainActivity, ProfileActivity::class.java)
//        startActivity(intent)
//        finish()
    }
}
