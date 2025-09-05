package com.saikou.sozo_tv.presentation.screens.splash

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.databinding.SplashScreenBinding
import com.saikou.sozo_tv.presentation.activities.MainActivity
import com.saikou.sozo_tv.utils.DialogUtils
import com.saikou.sozo_tv.utils.gone
import com.saikou.sozo_tv.utils.visible
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashScreen : Fragment() {
    private var _binding: SplashScreenBinding? = null
    private val binding get() = _binding!!
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var loadingDialog: Dialog

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
    }

    private fun setupNavigationLogic() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            binding.playerView.gone()
            lifecycleScope.launch {
                delay(1000)
                observeAndNavigate()
            }
        } else {
            binding.playerView.visible()
            initVideoPlayer()
        }
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
                when (playbackState) {
                    Player.STATE_BUFFERING -> binding.loadingIndicator.visible()
                    Player.STATE_READY -> {
                        binding.loadingIndicator.gone()
                        exoPlayer.play()
                    }

                    Player.STATE_ENDED -> {
                        exoPlayer.release()
                        observeAndNavigate()
                    }

                    Player.STATE_IDLE -> binding.loadingIndicator.visible()
                }
            }
        })
        exoPlayer.prepare()
    }

    private fun observeAndNavigate() {

        startActivity(Intent(requireContext(), MainActivity::class.java).apply {
            val options = ActivityOptions.makeCustomAnimation(
                requireContext(), R.anim.fade_in, R.anim.fade_out
            )
            startActivity(this, options.toBundle())
        })
        requireActivity().finish()


//        viewModel.isUpdateAvailableLiveData.observe(viewLifecycleOwner) { isUpdate ->
//            if (isUpdate) {
//                viewModel.getAppUpdateInfo()
//                viewModel.getAppUpdateInfo.observe(viewLifecycleOwner) { update ->
//                    showUpdateDialog(update)
//                }
//            } else {
//                viewModel.initSplash.observe(viewLifecycleOwner) { handleUserState(it) }
//                viewModel.isFirst.observe(viewLifecycleOwner, openLoginObserver)
//            }
//        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        if (::exoPlayer.isInitialized) exoPlayer.release()
        _binding = null
    }
}
