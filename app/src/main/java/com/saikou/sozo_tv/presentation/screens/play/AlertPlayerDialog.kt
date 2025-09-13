package com.saikou.sozo_tv.presentation.screens.play

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.databinding.AlertPlayerDialogBinding
import com.saikou.sozo_tv.utils.loadImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AlertPlayerDialog(private val entity: WatchHistoryEntity) : DialogFragment() {

    private var _binding: AlertPlayerDialogBinding? = null
    private val binding get() = _binding!!

    private lateinit var noClearListener: () -> Unit

    private lateinit var yesContinueListener: () -> Unit

    fun setNoClearListener(listener: () -> Unit) {
        noClearListener = listener
    }

    fun setYesContinueListener(listener: () -> Unit) {
        yesContinueListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AlertPlayerDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog!!.window?.setBackgroundDrawable(ColorDrawable(0))
        dialog!!.window?.setWindowAnimations(R.style.DialogAnimation)
        binding.coverImage.loadImage(entity.image)
        binding.movieTitle.text = entity.title
        binding.imdbRating.text = "IMDB: ${entity.rating}"
        binding.timeLast.text = entity.watchedAt.getReadableDateTime()
        binding.languageAnd.text = entity.description + " " + entity.language
        binding.continueTime.text = formatMillisToTime(entity.lastPosition)
        binding.noContinueBtn.setOnClickListener {
            noClearListener.invoke()
        }
        binding.yesContinueBtn.setOnClickListener {
            yesContinueListener.invoke()
        }
    }

    @SuppressLint("DefaultLocale")
    fun formatMillisToTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun Long.getReadableDateTime(): String {
        val date = Date(this)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(date)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}