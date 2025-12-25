package com.saikou.sozo_tv.utils

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.saikou.sozo_tv.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

val Int.dp: Float get() = (this / Resources.getSystem().displayMetrics.density)

fun String.getYearFromDate(): String? {
    return try {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = dateFormat.parse(this)
        val calendar = Calendar.getInstance()
        calendar.time = date!!
        calendar.get(Calendar.YEAR).toString()
    } catch (e: Exception) {
        null
    }
}

fun TextView.convertToTime(time: Int? = -1, context: Context) {
    if (time == null || time <= 0) {
        text = "0"
        return
    }
    val hour = time / 60
    val minute = time % 60

    text = when {
        hour > 0 && minute > 0 -> "$hour ${
            ContextCompat.getString(
                context,
                R.string.soat
            )
        } $minute ${ContextCompat.getString(context, R.string.daqiqa)}"

        hour > 0 -> "$hour ${ContextCompat.getString(context, R.string.soat)}"
        else -> "$minute ${ContextCompat.getString(context, R.string.daqiqa)}"
    }

}


fun String.readableAmount(): String {
    val list = this.mapIndexed { index, c ->
        if ((index + 1) % 3 == 0) "$c " else c.toString()
    }
    return list.joinToString("")
}

fun ImageView.loadImage(url: String?) {
    val rm = Glide.with(this)
    if (url.isNullOrBlank()) {
        rm.load(LocalData.anime404)
            .apply(
                RequestOptions()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error(R.drawable.planet)
            )
            .into(this)
        return
    }

    val fallbackRequest = rm.load(LocalData.anime404)
        .apply(
            RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(R.drawable.planet)
        )

    rm.load(url)
        .apply(
            RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
        )
        .error(fallbackRequest)
        .into(this)

}


fun View.showKeyboard() {
    val inputMethodManager =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    this.requestFocus()
    inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

fun EditText.hideKeyboard() {
    val inputMethodManager =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
}

fun View.resetStyle() {
    this.scaleX = 1.0f
    this.scaleY = 1.0f
    this.elevation = 0f
}

fun View.applyFocusedStyle(scaleY: Float = 1.07f, elevation: Float = 40f) {
    this.scaleX = 1.0f
    this.scaleY = scaleY
    this.elevation = elevation
}