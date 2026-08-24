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

        // Deliberately NOT `navProfile.isFocusedByDefault = true`. This screen is a
        // navigation rail with a content pane; marking the PANE as the window's default
        // focus put it in a race with the rail's own placement below and with the
        // adapter's first-bind requestFocus, so which one won came down to whether
        // View.post drained before or after first layout. The rail owns first focus.
        isSettingsOpen = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
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
            if (findNavController(R.id.nav_profile).popBackStack()) {
                backPressCount = 0
                return@addCallback
            }
            when (backPressCount) {
                0 -> {
                    Log.d("GGG", "onCreate: ${backPressCount} ")
                    focusRailToPosition(0)
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

    /**
     * Puts the rail on [position] and gives it the D-pad — once.
     *
     * The old body was `requestFocus(); smoothScrollToPosition(); requestFocus()`.
     * `smoothScrollToPosition` is a RecyclerView call that moves pixels and is
     * asynchronous; it does not change the leanback grid's own selected index. So the
     * second `requestFocus()` ran before the target row existed and handed focus to
     * whichever child happened to be attached. `selectedPosition` is the property the
     * grid tracks, and setting it moves both the scroll and the selection, so the one
     * `requestFocus()` afterwards lands where we asked.
     */
    private fun focusRailToPosition(position: Int) {
        viewBinding.profileRv.post {
            if (isFinishing || isDestroyed) return@post
            viewBinding.profileRv.selectedPosition = position
            viewBinding.profileRv.requestFocus()
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
        // A COPY. This used to alias LocalData.sectionList, the process-global list, and
        // then mutate it in place with no notifyItem* at all. On the recreate() path in
        // onStart the outgoing activity's RecyclerView is still attached to that same
        // instance while the incoming one edits it — the exact setup for RecyclerView's
        // "Inconsistency detected. Invalid item position" crash.
        val newSectionList = ArrayList(sectionList)
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

        // One placement, not two. Each branch used to run this AND an inline
        // `profileRv.post { ... }` doing the same thing, and the inline copy read
        // `accountList.size` at run time while the call read it eagerly — so if the
        // profile fetch resolved between them the two posts aimed at different rows
        // and issued conflicting scrolls.
        focusRailToPosition(accountList.size + if (isHistoryItemClicked) 4 else 2)
        if (!isSignedIn) {
            profileAdapter.updateAccountType("Guest")
            profileAdapter.setAccount()
        }

        // Index 1 is Sources (LocalData.sectionList: 0 My info, 1 Sources, 2 History,
        // 3 Bookmark, ...). It was 3, so "no source installed — go install one" from
        // EpisodeScreen opened Bookmarks. The comment on EXTRA_OPEN_SETTINGS below
        // says this deep link was fixed; only the extra's KEY had been.
        profileAdapter.setSectionSelected(if (isSettingsOpen) SECTION_SOURCES else SECTION_MY_INFO)

        profileAdapter.sectionClickListener { _, position ->
            // The rail navigates 300ms after focus settles, so this can fire once the
            // activity is already finishing, or before the NavHostFragment has been
            // created. findNavController throws in both cases, which is what crashed
            // the app on the way out of this screen.
            if (isFinishing || isDestroyed) return@sectionClickListener
            val navController = runCatching { findNavController(R.id.nav_profile) }.getOrNull()
                ?: return@sectionClickListener
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

                5 -> {
                    if (currentPageId != R.id.anilistScreen) navController.navigate(
                        R.id.anilistScreen,
                        null,
                        NavOptions.Builder().setPopUpTo(R.id.anilistScreen, true).build()
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

        /**
         * Shared so the two ends cannot drift apart again: the sender wrote
         * "openSettings" and the reader looked for "isSettings", so the only
         * deep link into Settings has never once opened Settings.
         */
        const val EXTRA_OPEN_SETTINGS = "openSettings"

        /** Positions in [com.saikou.sozo_tv.utils.LocalData.sectionList]. */
        private const val SECTION_MY_INFO = 0
        private const val SECTION_SOURCES = 1
    }

    override fun openLogin() {
        val intent = Intent(this, DeviceLoginActivity::class.java)
        startActivity(intent)
    }
}