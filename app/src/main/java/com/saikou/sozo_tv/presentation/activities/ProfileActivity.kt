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
import com.saikou.sozo_tv.data.repository.DeviceAuthRepository
import com.saikou.sozo_tv.data.model.SectionItem
import com.saikou.sozo_tv.data.model.anilist.Profile
import com.saikou.sozo_tv.databinding.ActivityProfileBinding
import com.saikou.sozo_tv.presentation.screens.profile.ExitDialog
import com.saikou.sozo_tv.presentation.screens.profile.MyAccountPage
import com.saikou.sozo_tv.presentation.viewmodel.SettingsViewModel
import com.saikou.sozo_tv.utils.LocalData.isHistoryItemClicked
import com.saikou.sozo_tv.utils.LocalData.sectionList
import com.saikou.sozo_tv.utils.finishDeferred
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class ProfileActivity : AppCompatActivity(), MyAccountPage.AuthNavigator {
    private lateinit var viewBinding: ActivityProfileBinding
    private var backPressCount = 0
    private val model: SettingsViewModel by viewModel()
    private val deviceAuth: DeviceAuthRepository by inject()

    private lateinit var profileAdapter: ProfileAdapter
    private var isSettingsOpen = false

    /** The sign-in state the rail was built for; see [onStart]. */
    private var builtSignedIn = false
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

    override fun onStart() {
        super.onStart()
        // The rail is built once, in onCreate. Linking normally relaunches this activity with
        // CLEAR_TASK, but any other return path from the login screen — Back during the
        // "signed in as" dwell, for instance — would otherwise strand a linked user on the guest
        // rail with no Exit row.
        if (builtSignedIn != deviceAuth.isSignedIn()) recreate()
    }

    private fun focusRecyclerViewToPosition(position: Int) {
        viewBinding.apply {
            profileRv.post {
                profileRv.requestFocus()
                profileRv.smoothScrollToPosition(position)
                profileRv.requestFocus()
            }
        }
    }

    private fun setUpRv() {
        model.profileData.observe(this) {
            // profileData now emits for guests too (it used to be gated on a token that was never
            // set). The signed-out rail is built synchronously below, so taking this branch as a
            // guest would add a second account row on top of it.
            if (!deviceAuth.isSignedIn()) return@observe
            profileAdapter.setAccount(it)
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
        val isSignedIn = deviceAuth.isSignedIn()
        builtSignedIn = isSignedIn
        // LocalData.sectionList is process-global, so this has to be idempotent in BOTH directions:
        // rebuilt after a sign-out the Exit row would otherwise linger and do nothing.
        newSectionList.removeAll { it.sectionImg == R.drawable.ic_exit }
        if (isSignedIn) newSectionList.add(
            SectionItem(MyApp.context.getString(R.string.exit), R.drawable.ic_exit)
        )
        profileAdapter = ProfileAdapter(
            accounts = accountList,
            sectionList = newSectionList,
            recyclerView = viewBinding.profileRv,
            isSignedIn = isSignedIn
        ).also { viewBinding.profileRv.adapter = it }

        if (!isHistoryItemClicked) {
            viewBinding.apply {
                profileRv.post {
                    profileRv.requestFocus()
                    val sectionPosition = accountList.size + 2
                    profileRv.smoothScrollToPosition(sectionPosition)
                    profileRv.requestFocus()
                }
            }
            focusRecyclerViewToPosition(accountList.size + 2)
        } else {
            focusRecyclerViewToPosition(accountList.size + 4)
            viewBinding.apply {
                profileRv.post {
                    profileRv.requestFocus()
                    val sectionPosition = accountList.size + 4
                    profileRv.smoothScrollToPosition(sectionPosition)
                    profileRv.requestFocus()
                }
            }

        }
        if (!isSignedIn) {
            profileAdapter.updateAccountType("Guest")
            profileAdapter.setAccount()
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
        finishDeferred()
    }

    companion object {
        const val HOME_BUTTON = -1
    }

    override fun openLogin() {
        val intent = Intent(this, DeviceLoginActivity::class.java)
        startActivity(intent)
    }
}