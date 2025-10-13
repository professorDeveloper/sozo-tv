package com.saikou.sozo_tv.components

import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.exoplayer.ExoPlayer
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.aniskip.AniSkip
import com.saikou.sozo_tv.aniskip.AniSkip.getType
import com.saikou.sozo_tv.presentation.viewmodel.PlayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SkipIntroView(
    private val controller: ConstraintLayout,
    private val player: ExoPlayer,
    private val viewModel: PlayViewModel,
    private val handler: Handler,
    private val malId: Int,
    private val episodeNumber: Int,
    private val episodeLength: Long
) {
    private var currentTimeStamp: AniSkip.Stamp? = null
    private var skippedTimeStamps: MutableList<AniSkip.Stamp> = mutableListOf()
    private lateinit var skipTimeButton: FrameLayout
    private lateinit var skipTimeText: TextView

    fun initialize() {
        val skipView = LayoutInflater.from(controller.context)
            .inflate(R.layout.skip_intro_layout, controller, false) as FrameLayout
        controller.addView(skipView)

        skipTimeButton = skipView.findViewById(R.id.skip_intro_button)
        skipTimeText = skipView.findViewById(R.id.skip_intro_text)

        // Load timestamps using ViewModel
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.loadTimeStamps(malId, episodeNumber, episodeLength, useProxyForTimeStamps = true)
        }

        updateTimeStamp()
    }

    private fun updateTimeStamp() {
            val playerCurrentTime = player.currentPosition / 1000
            currentTimeStamp = viewModel.timeStamps.value?.find { timestamp ->
                timestamp.interval.startTime < playerCurrentTime && playerCurrentTime < (timestamp.interval.endTime - 1)
            }

            val new = currentTimeStamp
            skipTimeText.text = if (new != null) {
                if (true) { // Replace with actual settings logic if needed
                    skipTimeButton.visibility = View.VISIBLE
                    skipTimeText.text = new.skipType.getType()
                    skipTimeButton.setOnClickListener {
                        player.seekTo((new.interval.endTime * 1000).toLong())
                    }
                }
                if (true && (new.skipType == "op" || new.skipType == "ed") && !skippedTimeStamps.contains(new)) { // Replace with actual autoSkip setting
                    player.seekTo((new.interval.endTime * 1000).toLong())
                    skippedTimeStamps.add(new)
                }
                new.skipType.getType()
            } else {
                skipTimeButton.visibility = View.GONE
                ""
            }
        handler.postDelayed({ updateTimeStamp() }, 500)
    }
}