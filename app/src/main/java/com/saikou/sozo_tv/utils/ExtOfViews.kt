package com.saikou.sozo_tv.utils

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
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
        null // Return null if the format is invalid
    }
}

//
//fun handleSectionClick(section: SectionItem, position: Int):Fragment? {
//    val fragment = when (position) {
//        0 -> MyFilmsPage()
//        1 -> MyAccountsPage()
//        2 -> MyCardsPage()
//        3 -> BookmarkPage()
//        4 -> HistoryPage()
//        5 -> SubscriptionPage()
//        6 -> TransactionPage()
//        else -> null
//    }
//
//    return fragment
//}
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
    Glide.with(this.context)
        .load(url)
        .apply(
            RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(R.drawable.placeholder)  // If image fails to load
        )
        .into(this)
}


class ZoomOutPageTransformer() :
    ViewPager2.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        if (position == 0.0f) {

            ObjectAnimator.ofFloat(view, "alpha", 0f, 1.0f)
                .setDuration((200 * 700).toLong()).start()
        }
    }
}

class CardTransformer(private val context: Context) : ViewPager2.PageTransformer {
    private val nextItemVisiblePx = 36.dp
    private val currentItemHorizontalMarginPx = 16.dp
    private val pageTranslationX = nextItemVisiblePx + currentItemHorizontalMarginPx

    override fun transformPage(page: View, position: Float) {
        val displayMetrics = context.resources.displayMetrics
        val dpHeight = displayMetrics.heightPixels / displayMetrics.density

        page.translationX = -pageTranslationX * position  /*dpHeight/(100)*/
        page.scaleY = 1 - (0.12f * kotlin.math.abs(position))
        page.alpha = 0.5f + (1 - kotlin.math.abs(position))
    }

}

class MediaPageTransformer : ViewPager2.PageTransformer {
    private fun parallax(view: View, position: Float) {
        if (position > -1 && position < 1) {
            val width = view.width.toFloat()
            view.translationX = -(position * width * 0.8f)
        }
    }

    override fun transformPage(view: View, position: Float) {
//        val bannerContainer = view.findViewById<View>(R.id.bannerImg)
//        parallax(bannerContainer, position)
    }
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

class HomeScrollTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        //page.translationX = -position * page.width / 2.0f

        //val params = RecyclerView.LayoutParams(
        //    RecyclerView.LayoutParams.MATCH_PARENT,
        //    0
        //)
        //page.layoutParams = params
        //progressBar?.layoutParams = params


    }
}