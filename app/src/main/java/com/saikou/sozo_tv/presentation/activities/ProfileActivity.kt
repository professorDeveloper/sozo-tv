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

    //    private val model: ProfileViewModel by viewModel()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
//        model.loadSubDetail()
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
//                    LocalData.isBookmarkClicked = false
                }

                2 -> {
                    Log.d("GGG", "onCreate:2BackPress ")
                    viewBinding.profileRv.requestFocus()
//                    LocalData.isBookmarkClicked = false
                    backPressCount = 0
                }

                else -> {
                    Log.d("GGG", "onCreate:Home :${backPressCount} ")
                    navigateHome()
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                model.subDetailState.observe(this@ProfileActivity) { state ->
//                    handleUserDataState(state)
//                }
            }
        }
//        lifecycleScope.launch {
//            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                model.exitState.collect { state ->
//                    handleExitState(state)
//                }
//            }
//        }
    }
//
//    private fun handleUserDataState(state: UiState<SubscriptionResponse>) {
//        when (state) {
//            is UiState.Error -> {
//                Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
//            }
//
//            is UiState.Success -> {
//                profileAdapter.updateAccountType(
//                    state.data.status
//                )
//                profileAdapter.setOnExitClickListener {
//                    val dialog = ExitDialog(
//                        data = state.data,
//                        subCode = model.getSub()
//                    )
//                    dialog.setNoClearListener {
//                        dialog.dismiss()
//                    }
//                    dialog.setYesContinueListener {
//                        model.exitUser()
//                        dialog.dismiss()
//                    }
//                    dialog.show(supportFragmentManager, "ExitDialog")
//                }
//            }
//
//            else -> {
//            }
//        }
//    }

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
//                0 -> {
//                    if (currentPageId != R.id.myAccountsPage) navController.navigate(
//                        R.id.myAccountsPage,
//                        null,
//                        NavOptions.Builder().setPopUpTo(R.id.myAccountsPage, true).build()
//                    )
//                }
//
//                1 -> {
//                    LocalData.isBookmarkClicked = false
//                    if (currentPageId != R.id.historyPage) navController.navigate(
//                        R.id.historyPage,
//                        null,
//                        NavOptions.Builder().setPopUpTo(R.id.historyPage, true).build()
//                    )
//                }
//
//                2 -> {
//                    LocalData.isBookmarkClicked = false
//                    if (currentPageId != R.id.bookmarkPage) navController.navigate(
//                        R.id.bookmarkPage,
//                        null,
//                        NavOptions.Builder().setPopUpTo(R.id.bookmarkPage, true).build()
//                    )
//                }
//
//                3 -> {
//                    LocalData.isBookmarkClicked = false
//                    if (currentPageId != R.id.messagePage) navController.navigate(
//                        R.id.messagePage,
//                        null,
//                        NavOptions.Builder().setPopUpTo(R.id.messagePage, true).build()
//                    )
//                }
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