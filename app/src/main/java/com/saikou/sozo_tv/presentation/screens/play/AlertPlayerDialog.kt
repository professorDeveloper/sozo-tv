package com.saikou.sozo_tv.presentation.screens.play

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.data.local.entity.WatchHistoryEntity
import com.saikou.sozo_tv.databinding.AlertPlayerDialogBinding
import com.saikou.sozo_tv.presentation.screens.play.dialog.applyGlassWindow
import com.saikou.sozo_tv.utils.loadImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AlertPlayerDialog(private val entity: WatchHistoryEntity? = null) : DialogFragment() {

    private var _binding: AlertPlayerDialogBinding? = null
    private val binding get() = _binding!!

    private var noClearListener: (() -> Unit)? = null
    private var yesContinueListener: (() -> Unit)? = null

    fun setNoClearListener(listener: () -> Unit) {
        noClearListener = listener
    }

    fun setYesContinueListener(listener: () -> Unit) {
        yesContinueListener = listener
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        yesContinueListener?.invoke()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = AlertPlayerDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.applyGlassWindow()

        val item = entity ?: run {
            dismissAllowingStateLoss()
            return
        }

        binding.coverImage.loadImage(item.image)
        binding.movieTitle.text = item.title
        binding.timeLast.text = item.watchedAt.getReadableDateTime()

        val rating = item.rating?.takeIf { it > 0.0 }
        binding.imdbRating.isVisible = rating != null
        if (rating != null) {
            binding.imdbRating.text = getString(
                R.string.history_rating, String.format(Locale.ROOT, "%.1f", rating)
            )
        }

        val summary = listOfNotNull(item.description, item.language)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        binding.languageAnd.isVisible = summary.isNotBlank()
        binding.languageAnd.text = summary

        binding.continueTime.text =
            getString(R.string.history_resume_from, formatMillisToTime(item.lastPosition))
        binding.noContinueBtn.setOnClickListener { noClearListener?.invoke() }
        binding.yesContinueBtn.setOnClickListener { yesContinueListener?.invoke() }

        binding.yesContinueBtn.post {
            _binding?.yesContinueBtn?.requestFocus()
        }
    }

    fun formatMillisToTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

        return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
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
