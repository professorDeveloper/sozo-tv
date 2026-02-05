package com.saikou.sozo_tv.presentation.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.ProfileAdapter
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.data.local.pref.AuthPrefKeys
import com.saikou.sozo_tv.data.local.pref.PreferenceManager
import com.saikou.sozo_tv.data.model.SectionItem
import com.saikou.sozo_tv.data.model.anilist.Profile
import com.saikou.sozo_tv.databinding.ActivityProfileBinding
import com.saikou.sozo_tv.presentation.screens.profile.ExitDialog
import com.saikou.sozo_tv.presentation.screens.profile.MyAccountPage
import com.saikou.sozo_tv.presentation.viewmodel.SettingsViewModel
import com.saikou.sozo_tv.utils.LocalData.isHistoryItemClicked
import com.saikou.sozo_tv.utils.LocalData.sectionList
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ProfileActivity : AppCompatActivity(), MyAccountPage.AuthNavigator {
    private lateinit var viewBinding: ActivityProfileBinding
    private var backPressCount = 0
    private val model: SettingsViewModel by viewModel()

    private lateinit var profileAdapter: ProfileAdapter
    private var isSettingsOpen = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        model.loadProfile()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            viewBinding.navProfile.isFocusedByDefault = true
        }
        isSettingsOpen = intent.getBooleanExtra("isSettings", false)
        setUpRv()
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.seasonalTheme.collect { theme ->
                    viewBinding.seasonalBackground.setTheme(theme)
                }
            }
        }

        isHistoryItemClicked = false
        onBackPressedDispatcher.addCallback(this) {
            when (backPressCount) {
                0 -> {
                    Log.d("GGG", "onCreate: ${backPressCount} ")
                    focusRecyclerViewToPosition(0)
                    backPressCount = 1
                }

                2 -> {
                    Log.d("GGG", "onCreate:2BackPress ")
                    viewBinding.profileRv.requestFocus()
                    backPressCount = 0
                }


                else -> {
                    Log.d("GGG", "onCreate:Home :${backPressCount} ")
                    navigateHome()
                }
            }
        }

    }

    private fun focusRecyclerViewToPosition(position: Int) {
        viewBinding.apply {
            profileRv.post {
                profileRv.requestFocusFromTouch()
                profileRv.smoothScrollToPosition(position)
                profileRv.requestFocusFromTouch()
            }
        }
    }

    private fun setUpRv() {
        model.profileData.observe(this) {
            profileAdapter.addAccount(it)
            profileAdapter.updateAccountType("Basic")
            profileAdapter.setOnExitClickListener {
                val dialog = ExitDialog(
                    data = it
                )
                dialog.setNoClearListener {
                    dialog.dismiss()
                }
                dialog.setYesContinueListener {
                    model.exitUser()
                    val mainActivity = Intent(this, MainActivity::class.java)
                    mainActivity.flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(mainActivity)
                    dialog.dismiss()
                }
                dialog.show(supportFragmentManager, "ExitDialog")
            }
        }

        val accountList = arrayListOf<Profile>()
        val newSectionList = sectionList
        if (PreferenceManager().getString(AuthPrefKeys.ANILIST_TOKEN)
                .isNotEmpty()
            && sectionList.find { it.sectionImg == R.drawable.ic_exit } == null
        ) newSectionList.add(
            SectionItem(MyApp.context.getString(R.string.exit), R.drawable.ic_exit)
        )
        profileAdapter = ProfileAdapter(
            accounts = accountList,
            sectionList = newSectionList,
            recyclerView = viewBinding.profileRv
        ).also { viewBinding.profileRv.adapter = it }

        if (!isHistoryItemClicked) {
            viewBinding.apply {
                profileRv.post {
                    profileRv.requestFocusFromTouch()
                    val sectionPosition = accountList.size + 2
                    profileRv.smoothScrollToPosition(sectionPosition)
                    profileRv.requestFocusFromTouch()
                }
            }
            focusRecyclerViewToPosition(accountList.size + 2)
        } else {
            focusRecyclerViewToPosition(accountList.size + 4)
            viewBinding.apply {
                profileRv.post {
                    profileRv.requestFocusFromTouch()
                    val sectionPosition = accountList.size + 4
                    profileRv.smoothScrollToPosition(sectionPosition)
                    profileRv.requestFocusFromTouch()
                }
            }

        }
        val preference = PreferenceManager()
        val token = preference.getString(AuthPrefKeys.ANILIST_TOKEN)
        if (token.isEmpty()) {
            profileAdapter.updateAccountType("Guest")
            profileAdapter.addAccount()
        }

        profileAdapter.setSectionSelected(if (isSettingsOpen) 3 else 0)

        profileAdapter.sectionClickListener { _, position ->
            val navController = findNavController(R.id.nav_profile)
            val currentPageId = navController.currentDestination?.id
            backPressCount = 2

            when (position) {
                HOME_BUTTON -> navigateHome()

                0 -> {
                    if (currentPageId != R.id.myAccountPage) navController.navigate(
                        R.id.myAccountPage,
                        null,
                        NavOptions.Builder().setPopUpTo(R.id.myAccountPage, true).build()
                    )
                }

                1 -> {
                    if (currentPageId != R.id.sourceScreen) navController.navigate(
                        R.id.sourceScreen,
                        null,
                        NavOptions.Builder().setPopUpTo(R.id.sourceScreen, true).build()
                    )
                }

                3 -> {
                    if (currentPageId != R.id.bookmarkScreen) navController.navigate(
                        R.id.bookmarkScreen,
                        null,
                        NavOptions.Builder().setPopUpTo(R.id.bookmarkScreen, true).build()
                    )
                }

                2 -> {
                    if (currentPageId != R.id.historyPage) navController.navigate(
                        R.id.historyPage,
                        null,
                        NavOptions.Builder().setPopUpTo(R.id.historyPage, true).build()
                    )
                }

                4 -> {
                    if (currentPageId != R.id.newsPage) navController.navigate(
                        R.id.newsPage,
                        null,
                        NavOptions.Builder().setPopUpTo(R.id.newsPage, true).build()
                    )
                }
            }
        }


    }

    private fun navigateHome() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        const val HOME_BUTTON = -1
    }

    override fun openLogin() {
        val intent = Intent(this, QrLoginActivity::class.java)
        startActivity(intent)
    }
}