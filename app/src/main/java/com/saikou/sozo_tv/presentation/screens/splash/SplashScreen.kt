package com.saikou.sozo_tv.presentation.screens.splash

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.extensions.ExtensionEngine
import com.saikou.sozo_tv.databinding.SplashScreenBinding
import com.saikou.sozo_tv.domain.model.AppUpdate
import com.saikou.sozo_tv.presentation.activities.MainActivity
import com.saikou.sozo_tv.presentation.activities.UpdateActivity
import com.saikou.sozo_tv.presentation.viewmodel.SplashViewModel
import com.saikou.sozo_tv.utils.DialogUtils
import com.saikou.sozo_tv.utils.Resource
import com.saikou.sozo_tv.utils.finishDeferred
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.lang.ref.WeakReference

@SuppressLint("CustomSplashScreen")
class SplashScreen : Fragment() {
    private var _binding: SplashScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var loadingDialog: Dialog
    private val viewModel: SplashViewModel by viewModel()
    private val engine: ExtensionEngine by inject()

    // The splash must never become a dead end, and each of the three ways out of the intro
    // (video ended, video failed, watchdog fired) can race the others. Both hand-offs are
    // one-shot so a second trigger is a no-op rather than a duplicate MainActivity.
    private var introFinished = false
    private var navigated = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SplashScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadingDialog = DialogUtils.loadingDialog(requireContext())
        setupNavigationLogic()
        viewModel.checkSubscribe()
    }

    private fun setupNavigationLogic() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            binding.playerView.gone()
            lifecycleScope.launch {
                delay(1000)
                finishIntro()
            }
        } else {
            binding.playerView.visible()
            initVideoPlayer()
            startIntroWatchdog()
        }
    }

    /**
     * A decoder that never reports `STATE_ENDED` — a stalled or unsupported codec on a given TV —
     * would otherwise strand the user on the intro forever. The clip is ~4.5s, so anything past
     * [INTRO_TIMEOUT_MS] means playback is not coming back.
     */
    private fun startIntroWatchdog() {
        lifecycleScope.launch {
            delay(INTRO_TIMEOUT_MS)
            if (!introFinished) Log.w("SplashScreen", "intro video stalled — skipping")
            finishIntro()
        }
    }

    /** Leave the intro exactly once, whichever of the three triggers gets here first. */
    private fun finishIntro() {
        if (introFinished || !isAdded) return
        introFinished = true
        if (::exoPlayer.isInitialized) exoPlayer.release()
        observeAndNavigate()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initVideoPlayer() {
        val renderersFactory = DefaultRenderersFactory(requireContext()).apply {
            setEnableDecoderFallback(true)
            forceEnableMediaCodecAsynchronousQueueing()
        }
        val trackSelector = DefaultTrackSelector(requireContext()).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSizeSd()
                    .setForceLowestBitrate(true)
            )
        }
        exoPlayer = ExoPlayer.Builder(requireContext(), renderersFactory)
            .setTrackSelector(trackSelector)
            .build()
        binding.playerView.player = exoPlayer
        binding.loadingIndicator.visible()

        val mediaItem = MediaItem.fromUri(Uri.parse("asset:///splash.mp4"))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (_binding == null) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> binding.loadingIndicator.visible()
                    Player.STATE_READY -> {
                        binding.loadingIndicator.gone()
                        exoPlayer.play()
                    }

                    Player.STATE_ENDED -> finishIntro()

                    Player.STATE_IDLE -> binding.loadingIndicator.visible()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("SplashScreen", "splash video failed", error)
                finishIntro()
            }
        })
        exoPlayer.prepare()
    }

    private fun observeAndNavigate() {
        // The view model publishes the update payload before the flag and emits the flag exactly
        // once — including on timeout — so this branch always runs and never re-registers.
        viewModel.isUpdateAvailableLiveData.observe(viewLifecycleOwner) { isUpdate ->
            val update = viewModel.getAppUpdateInfo.value
            if (isUpdate && update != null) {
                showUpdateDialog(update)
            } else {
                viewModel.initSplash.observe(viewLifecycleOwner) { handleUserState(it) }
            }
        }
    }

    private fun handleUserState(state: Resource<Unit>) {
        when (state) {
            is Resource.Loading -> loadingDialog.show()
            is Resource.Success -> {
                loadingDialog.dismiss()
                runFirstLaunchSetupThenEnter()
            }

            is Resource.Error -> {
                loadingDialog.dismiss()
                Toast.makeText(requireContext(), state.throwable.message, Toast.LENGTH_SHORT).show()
                Log.e("SplashScreen", "Subscription error", state.throwable)
            }

            else -> {}
        }
    }

    /**
     * First-launch only: if no source is active yet, auto-install the default repos behind a
     * "Setting up sources…" indicator so Home has content without the user visiting Sources.
     * If a source is already active, enter Home immediately.
     *
     * The install downloads dozens of plugins across three repo groups; on a slow or
     * rate-limited link that aggregate runs for minutes, so entry to the app is never gated on
     * it finishing. It runs on the engine's own scope and we wait only until a source becomes
     * usable — at most [SETUP_WAIT_MS] — then enter Home while the rest installs behind us.
     */
    private fun runFirstLaunchSetupThenEnter() {
        if (engine.hasActiveProvider()) {
            navigateToMain()
            return
        }
        binding.loadingIndicator.visible()
        binding.setupStatus.visible()
        binding.setupStatus.text = "Setting up sources…"
        // The install outlives this fragment, so its progress callback must not pin the view
        // hierarchy: hold the screen weakly and drop the update once the view is gone.
        val handler = Handler(Looper.getMainLooper())
        val self = WeakReference(this)
        val install = engine.installDefaultsAsync { name, current, total ->
            handler.post {
                val b = self.get()?._binding ?: return@post
                b.setupStatus.text = if (total > 0) "Setting up $name… $current/$total"
                else "Setting up $name…"
            }
        }
        lifecycleScope.launch {
            withTimeoutOrNull(SETUP_WAIT_MS) {
                while (!engine.hasActiveProvider() && install.isActive) delay(SETUP_POLL_MS)
            }
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        if (navigated || !isAdded) return
        navigated = true
        val options = ActivityOptions.makeCustomAnimation(
            requireContext(), R.anim.fade_in, R.anim.fade_out
        )
        startActivity(Intent(requireContext(), MainActivity::class.java), options.toBundle())
        requireActivity().finishDeferred()
    }

//    private fun showUpdateDialog(appUpdate: AppUpdate) {
//        startActivity(
//            UpdateActivity.newIntent(
//                requireActivity(),
//                appUpdate
//            )
//        )
//        requireActivity().finish()
//    }

//    private fun handleUserState(state: Resource<SubscriptionResponse>) {
//        when (state) {
//            is Resource.Loading -> loadingDialog.show()
//            is Resource.Success -> {
//                loadingDialog.dismiss()
//                startActivity(Intent(requireContext(), MainActivity::class.java).apply {
//                    val options = ActivityOptions.makeCustomAnimation(
//                        requireContext(), R.anim.fade_in, R.anim.fade_out
//                    )
//                    startActivity(this, options.toBundle())
//                })
//                requireActivity().finish()
//            }
//
//            is Resource.Error -> {
//                loadingDialog.dismiss()
//                Toast.makeText(requireContext(), state.throwable.message, Toast.LENGTH_SHORT).show()
//                findNavController().navigate(R.id.phoneScreen, null, navOptions())
//                Log.e("SplashScreen", "Subscription error", state.throwable)
//            }
//
//            else -> {}
//        }
//    }

//    private val openLoginObserver = androidx.lifecycle.Observer<Unit> {
//        findNavController().navigate(R.id.phoneScreen, null, navOptions())
//    }

    //    private fun navOptions(): NavOptions = NavOptions.Builder()
//        .setPopUpTo(R.id.splashScreen, true)
//        .setEnterAnim(R.anim.fade_in)
//        .setExitAnim(R.anim.fade_out)
//        .setPopEnterAnim(R.anim.fade_in)
//        .setPopExitAnim(R.anim.fade_out)
//        .build()
    private fun showUpdateDialog(appUpdate: AppUpdate) {
        startActivity(
            UpdateActivity.newIntent(
                requireActivity(),
                appUpdate
            )
        )
        requireActivity().finishDeferred()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::exoPlayer.isInitialized) exoPlayer.release()
        _binding = null
    }

    private companion object {
        /** Generous cap on the ~4.5s intro clip; only trips when playback has stalled. */
        const val INTRO_TIMEOUT_MS = 12_000L

        /** How long the splash waits for first-launch setup to produce a usable source. */
        const val SETUP_WAIT_MS = 20_000L
        const val SETUP_POLL_MS = 250L
    }
}
