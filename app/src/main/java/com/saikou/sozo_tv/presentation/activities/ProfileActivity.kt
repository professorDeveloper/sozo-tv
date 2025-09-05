package com.saikou.sozo_tv.presentation.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.ProfileAdapter
import com.saikou.sozo_tv.databinding.ActivityProfileBinding
import com.saikou.sozo_tv.utils.LocalData
import com.saikou.sozo_tv.utils.LocalData.sectionList
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityProfileBinding
    private var backPressCount = 0

    private lateinit var profileAdapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            viewBinding.navProfile.isFocusedByDefault = true
        }
        setUpRv()
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
        val accountList = arrayListOf<String>()
        profileAdapter = ProfileAdapter(
            accounts = accountList,
            sectionList = sectionList,
            recyclerView = viewBinding.profileRv
        ).also { viewBinding.profileRv.adapter = it }

        if (!LocalData.isHistoryItemClicked) {
            viewBinding.apply {
                profileRv.post {
                    profileRv.requestFocusFromTouch()
                    val sectionPosition = accountList.size + 2
                    profileRv.smoothScrollToPosition(sectionPosition)
                    profileRv.requestFocusFromTouch()
                }
            }
            profileAdapter.addAccount("Guest")

            focusRecyclerViewToPosition(accountList.size + 2)
        } else {
            focusRecyclerViewToPosition(accountList.size + 3)
            viewBinding.apply {
                profileRv.post {
                    profileRv.requestFocusFromTouch()
                    val sectionPosition = accountList.size + 3
                    profileRv.smoothScrollToPosition(sectionPosition)
                    profileRv.requestFocusFromTouch()
                }
            }
            profileAdapter.addAccount("Guest")

        }


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
            }
        }
        profileAdapter.setOnExitClickListener {

        }
        profileAdapter.updateAccountType(
            "Guest"
        )

    }

    private fun navigateHome() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        backPressCount = 0
    }

    companion object {
        const val HOME_BUTTON = -1
    }
}