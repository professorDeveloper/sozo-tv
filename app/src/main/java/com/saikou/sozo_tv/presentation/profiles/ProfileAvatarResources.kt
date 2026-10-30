package com.saikou.sozo_tv.presentation.profiles

import android.graphics.Color
import androidx.annotation.DrawableRes
import com.saikou.sozo_tv.R

/** Stable preset ids shared with the mobile app and the profiles API. */
object ProfileAvatarResources {
    val selectableIds: List<String> = buildList {
        (1..16).forEach { add("lorelei-$it") }
        (1..16).forEach { add("open-peeps-$it") }
        (1..8).forEach { add("notionists-$it") }
    }

    private val allDrawables = mapOf(
        "lorelei-1" to R.drawable.lorelei_1,
        "lorelei-2" to R.drawable.lorelei_2,
        "lorelei-3" to R.drawable.lorelei_3,
        "lorelei-4" to R.drawable.lorelei_4,
        "lorelei-5" to R.drawable.lorelei_5,
        "lorelei-6" to R.drawable.lorelei_6,
        "lorelei-7" to R.drawable.lorelei_7,
        "lorelei-8" to R.drawable.lorelei_8,
        "lorelei-9" to R.drawable.lorelei_9,
        "lorelei-10" to R.drawable.lorelei_10,
        "lorelei-11" to R.drawable.lorelei_11,
        "lorelei-12" to R.drawable.lorelei_12,
        "lorelei-13" to R.drawable.lorelei_13,
        "lorelei-14" to R.drawable.lorelei_14,
        "lorelei-15" to R.drawable.lorelei_15,
        "lorelei-16" to R.drawable.lorelei_16,
        "open-peeps-1" to R.drawable.open_peeps_1,
        "open-peeps-2" to R.drawable.open_peeps_2,
        "open-peeps-3" to R.drawable.open_peeps_3,
        "open-peeps-4" to R.drawable.open_peeps_4,
        "open-peeps-5" to R.drawable.open_peeps_5,
        "open-peeps-6" to R.drawable.open_peeps_6,
        "open-peeps-7" to R.drawable.open_peeps_7,
        "open-peeps-8" to R.drawable.open_peeps_8,
        "open-peeps-9" to R.drawable.open_peeps_9,
        "open-peeps-10" to R.drawable.open_peeps_10,
        "open-peeps-11" to R.drawable.open_peeps_11,
        "open-peeps-12" to R.drawable.open_peeps_12,
        "open-peeps-13" to R.drawable.open_peeps_13,
        "open-peeps-14" to R.drawable.open_peeps_14,
        "open-peeps-15" to R.drawable.open_peeps_15,
        "open-peeps-16" to R.drawable.open_peeps_16,
        "notionists-1" to R.drawable.notionists_1,
        "notionists-2" to R.drawable.notionists_2,
        "notionists-3" to R.drawable.notionists_3,
        "notionists-4" to R.drawable.notionists_4,
        "notionists-5" to R.drawable.notionists_5,
        "notionists-6" to R.drawable.notionists_6,
        "notionists-7" to R.drawable.notionists_7,
        "notionists-8" to R.drawable.notionists_8,
        "thumbs-1" to R.drawable.thumbs_1,
        "thumbs-2" to R.drawable.thumbs_2,
        "thumbs-3" to R.drawable.thumbs_3,
        "thumbs-4" to R.drawable.thumbs_4,
    )

    private val colors = listOf(
        "#E50914", "#F5A623", "#2ECC71", "#1E88E5",
        "#8E44AD", "#E91E63", "#00ACC1", "#FF7043",
    )

    @DrawableRes
    fun drawable(id: String?): Int? = allDrawables[id]

    fun fallbackColor(hex: String?, seed: String): Int = runCatching {
        Color.parseColor(hex)
    }.getOrElse {
        Color.parseColor(colors[kotlin.math.abs(seed.hashCode()) % colors.size])
    }
}
