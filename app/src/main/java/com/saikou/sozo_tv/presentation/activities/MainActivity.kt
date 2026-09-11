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
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.saikou.sozo_tv.data.extensions.ExtensionContentRegistry
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.data.remote.remote.RemoteCommand
import com.saikou.sozo_tv.data.repository.RemoteControlManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

// AppCompatActivity (not FragmentActivity): CloudStream plugins cast the context handed to
// `Plugin.load()` to AppCompatActivity, and this is the screen in the foreground while the
// extension engine loads them. `Theme.Tv` descends from Theme.MaterialComponents, so it qualifies.
class MainActivity : AppCompatActivity() {
    private val model: SettingsViewModel by viewModel()
    private val remote: RemoteControlManager by inject()
    private val engine: ExtensionEngine by inject()
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
        observeRemote()
    }

    /**
     * Acts on what the phone sends.
     *
     * Only the commands that are about getting somewhere live here — search
     * text, opening a title, and the d-pad. Playback commands are handled by
     * the player, which is the only thing that knows whether there is anything
     * playing.
     *
     * repeatOnLifecycle(STARTED) so a backgrounded TV stops acting on presses:
     * the channel stays open, but driving navigation while another app is in
     * the foreground is not something a remote should do.
     */
    private fun observeRemote() {
        remote.start()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // Sticky: a command that arrived while this screen was not
                    // showing is still worth acting on the moment it is — that
                    // is what "Play on TV" from a phone means.
                    remote.navigation.collect { command ->
                        if (command == null) return@collect
                        when (command.type) {
                            "text" -> command.text?.let(::openSearch)
                            "open" -> openTitle(command)
                            "home" -> navigateHome()
                        }
                        remote.consumeNavigation(command)
                    }
                }
                remote.commands.collect { command ->
                    when (command.type) {
                        "back" -> onBackPressedDispatcher.onBackPressed()
                        "dpad" -> command.direction?.let(::sendDpad)
                    }
                }
            }
        }
    }

    /**
     * Typing on the phone lands in the TV's search box.
     *
     * The reason the remote exists. Navigating there rather than injecting
     * keystrokes into whatever happens to be focused: a d-pad cannot move a
     * caret, so "type into the focused field" is only meaningful when that
     * field is the search box anyway.
     */
    private fun openSearch(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navMainFragment.id) as? NavHostFragment
                ?: return
        navHostFragment.navController.navigate(
            R.id.search,
            Bundle().apply {
                putString(SearchScreen.ARG_QUERY, text)
                putBoolean(SearchScreen.ARG_SEARCH_ALL, true)
            },
        )
    }

    /**
     * "Play on TV" from the phone.
     *
     * The phone sends the title's own source and url. When this TV has that source installed,
     * the title opens directly — searching by name picked whichever source matched first, which
     * is often not the one the viewer was looking at. Otherwise the name search is the fallback.
     */
    private fun openTitle(command: RemoteCommand) {
        val title = command.title?.takeIf { it.isNotBlank() }
        val url = command.contentUrl?.takeIf { it.isNotBlank() }
        val provider = command.provider?.takeIf { it.isNotBlank() }?.let(::tvProviderId)
        if (url == null || provider == null) {
            title?.let(::openSearch)
            return
        }
        lifecycleScope.launch {
            if (engine.hasProvider(provider)) {
                val id = ExtensionContentRegistry.encode(
                    provider = provider,
                    url = url,
                    isAnime = provider.startsWith("an:"),
                    title = title,
                )
                startActivity(
                    Intent(this@MainActivity, PlayerActivity::class.java).putExtra("model", id)
                )
            } else {
                title?.let(::openSearch)
            }
        }
    }

    /** The phone's source ids: `cs:`/`an:` match ours; a bare id is a Sozo server source. */
    private fun tvProviderId(phoneProvider: String): String = when {
        phoneProvider.startsWith("cs:") || phoneProvider.startsWith("an:") ||
            phoneProvider.startsWith("sv:") -> phoneProvider
        phoneProvider.contains(':') -> phoneProvider
        else -> "sv:$phoneProvider"
    }

    private fun navigateHome() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navMainFragment.id) as? NavHostFragment
                ?: return
        navHostFragment.navController.popBackStack(R.id.home, false)
    }

    /** Synthesised so the phone's arrows move focus exactly as the real remote's do. */
    private fun sendDpad(direction: String) {
        val code = when (direction) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP
            "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT
            "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "center" -> KeyEvent.KEYCODE_DPAD_CENTER
            else -> return
        }
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun setupBackBehaviour() {
        onBackPressedDispatcher.addCallback(this) {
            val rail = binding.navMain
            if (rail.isVisible && !rail.hasFocus()) {
                rail.requestFocus()
                return@addCallback
            }
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.navMainFragment.id) as NavHostFragment
        val navController = navHostFragment.navController

        binding.navMain.setupWithNavController(navController)

        intent?.getStringExtra(EXTRA_SEARCH_QUERY)?.takeIf { it.isNotBlank() }?.let { query ->
            intent.removeExtra(EXTRA_SEARCH_QUERY)
            navController.navigate(
                R.id.search,
                Bundle().apply {
                    putString(SearchScreen.ARG_QUERY, query)
                    putBoolean(SearchScreen.ARG_SEARCH_ALL, true)
                },
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.navMainFragment.isFocusedByDefault = true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            Log.d("Navigation", "Destination changed: ${destination.id}")

            // Once per activity. This ran on every destination change and each run added
            // another profile observer that lived as long as the activity.
            if (headerBinding == null) setupHeader()

            when (destination.id) {
                R.id.search, R.id.home, R.id.categories, R.id.contact, R.id.tvgarden ->
                    binding.navMain.visibility = View.VISIBLE

                else -> binding.navMain.visibility = View.GONE
            }
        }
    }

    private fun setupHeader() {
        val headerView = binding.navMain.headerView ?: return
        val header = ContentHeaderMenuMainTvBinding.bind(headerView)
        headerBinding = header
        handleUserDataState(header)
        header.root.setOnClickListener {
            navigateProfile()
        }
        headerView.setOnOpenListener {
            header.headerContainer.visibility = View.VISIBLE
        }
        headerView.setOnCloseListener {
            header.headerContainer.visibility = View.GONE
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
        const val EXTRA_SEARCH_QUERY = "sozo.extra.SEARCH_QUERY"
    }

}