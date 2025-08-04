package com.saikou.sozo_tv.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

fun String.toDateFromIso8601(): Date? {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
    dateFormat.timeZone = TimeZone.getTimeZone("UTC") // UTC vaqt zonasini sozlash
    return try {
        dateFormat.parse(this)
    } catch (e: Exception) {
        null
    }
}
@SuppressLint("NewApi")
fun snackString(s: String?, activity: Activity? = null, clipboard: String? = null) {
    if (s != null) {
        (activity)?.apply {
            runOnUiThread {
                val snackBar = Snackbar.make(
                    window.decorView.findViewById(android.R.id.content),
                    s,
                    Snackbar.LENGTH_LONG
                )
                snackBar.view.apply {
                    updateLayoutParams<FrameLayout.LayoutParams> {
                        gravity = (Gravity.CENTER_HORIZONTAL or Gravity.TOP)
                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    translationY = (24f + 32f)
                    translationZ = 32f
                    val shapeDrawable = ShapeDrawable()
                    shapeDrawable.paint.setColor(activity.getColor(R.color.main_background))// Set the background color if needed
                    shapeDrawable.paint.style = Paint.Style.FILL
                    shapeDrawable.shape = RoundRectShape(
                        floatArrayOf(120f, 120f, 120f, 120f, 120f, 120f, 120f, 120f),
                        null,
                        null
                    )

                    this.background = shapeDrawable
                    setOnClickListener {
                        snackBar.dismiss()
                    }
                    setOnLongClickListener {
                        true
                    }
                }
                snackBar.show()
            }
        }
    }
}

fun RecyclerView.setupGridLayoutForCategories(adapter: CategoriesPageAdapter) {
    val gridLayoutManager =
        GridLayoutManager(this.context, CategoriesPageAdapter.COLUMN_COUNT)
    gridLayoutManager.orientation = RecyclerView.VERTICAL
    gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            return when (adapter.getItemViewType(position)) {
                CategoriesPageAdapter.TYPE_CATEGORY -> 1
                else -> CategoriesPageAdapter.COLUMN_COUNT
            }
        }
    }
    this.layoutManager = gridLayoutManager
    this.descendantFocusability = RecyclerView.FOCUS_AFTER_DESCENDANTS
    this.isFocusable = true
    this.isFocusableInTouchMode = true
}


fun String.toDateFromIso8601ForTxt(): String? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8+ versiyalar uchun java.time API
            val instant = LocalDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME)
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
            instant.format(formatter)
        } else {
            // Android 5-7 versiyalar uchun SimpleDateFormat
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

            val date: Date? = inputFormat.parse(this)
            date?.let { outputFormat.format(it) } ?: "Wrong format"
        }
    } catch (e: Exception) {
        "Wrong format"
    }
}


fun View.visible() {
    this.visibility = View.VISIBLE
}


fun View.gone() {
    this.visibility = View.GONE
}
//
//
//var selectedCategory = ArrayList<SubCategoryItem>()
//
//
//fun ArrayList<SubCategoryItem>.filterChannelsByCategory(
//    categoryId: Int
//): ArrayList<SubCategoryItem> {
//    return this.filter { it.category.toInt() == categoryId } as ArrayList<SubCategoryItem>
//}
//
//fun ArrayList<ChannelResponseItem>.filterByKeywords(
//    keywords: List<SubCategoryItem>
//): List<ChannelResponseItem> {
//    Log.d("GG", "filterByKeywords:${keywords} ")
//    Log.d("GG", "filterByKeywords:${this} ")
//    return this.filter { item ->
//        keywords.any { keyword ->
//            if (item.categoryProperty == null) false else item.categoryProperty.contains(
//                keyword.property_name,
//                ignoreCase = true
//            )
//        }
//    }
//}
//
//
//fun ArrayList<MovieBookmark>.toMovieList(): ArrayList<Movie> {
//    return this.map {
//        Movie(
//            id = it.id,
//            name = it.title,
//            image = it.image,
//            categoryProperty = it.categoryProperty,
//            categoryid = it.categoryid ?: "Unknown",
//            country = it.country ?: "Unknown",
//            description = it.description,
//            language = it.language ?: "Unknown",
//            rating = it.rating ?: 0.0,
//            release_year = it.release_year
//        )
//    } as ArrayList<Movie>
//}
//
//
//fun RecyclerView.setupGridLayoutForCategories(adapter: CategoriesPageAdapter) {
//    val gridLayoutManager =
//        GridLayoutManager(this.context, CategoriesPageAdapter.COLUMN_COUNT)
//    gridLayoutManager.orientation = RecyclerView.VERTICAL
//    gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
//        override fun getSpanSize(position: Int): Int {
//            return when (adapter.getItemViewType(position)) {
//                CategoriesPageAdapter.TYPE_CATEGORY -> 1
//                else -> CategoriesPageAdapter.COLUMN_COUNT
//            }
//        }
//    }
//    this.layoutManager = gridLayoutManager
//    this.descendantFocusability = RecyclerView.FOCUS_AFTER_DESCENDANTS
//    this.isFocusable = true
//    this.isFocusableInTouchMode = true
//}

fun makeCustomHttpClient(sslContext: SSLContext): OkHttpClient {
    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        .hostnameVerifier { _, _ -> true }
        .build()
}


suspend fun extractTarFile(tarUrl: String, outputDir: File) {
    withContext(Dispatchers.IO) {
        try {
            val url = URL(tarUrl)
            val connection = url.openConnection()
            if (connection is HttpsURLConnection) {
                disableSSLCertificateChecking(connection)
            }

            connection.getInputStream().use { inputStream ->
                TarInputStream(BufferedInputStream(inputStream)).use { tarInputStream ->
                    var entry: TarEntry?
                    while (tarInputStream.nextEntry.also { entry = it } != null) {
                        val outputFile = File(outputDir, entry!!.name)
                        if (entry!!.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(outputFile)).use { outputStream ->
                                tarInputStream.copyTo(outputStream)
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            println("Failed to extract TAR file: ${e.message}")
        }
    }
}


fun disableSSLCertificateChecking(connection: HttpsURLConnection) {
    try {
        // Ishonchsiz TrustManager: barcha sertifikatlarni tekshiruvsiz qabul qiladi
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        connection.sslSocketFactory = sslContext.socketFactory
        // Barcha host nomlarini qabul qilish
        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}


fun makeSslForTrailer(sslContext: SSLContext) {
    sslContext.init(null, arrayOf(object : X509TrustManager {
        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?
        ) {
        }

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?
        ) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }), SecureRandom())
}
//
//fun RecyclerView.setupGridLayoutForCategoriesNum4(adapter: CategoriesPageAdapter) {
//    val gridLayoutManager =
//        GridLayoutManager(this.context, CategoriesPageAdapter.COLUMN_COUNT_NUM4)
//    gridLayoutManager.orientation = RecyclerView.VERTICAL
//    gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
//        override fun getSpanSize(position: Int): Int {
//            return when (adapter.getItemViewType(position)) {
//                CategoriesPageAdapter.TYPE_CATEGORY -> 1
//                else -> CategoriesPageAdapter.COLUMN_COUNT_NUM4
//            }
//        }
//    }
//    this.layoutManager = gridLayoutManager
//    this.descendantFocusability = RecyclerView.FOCUS_AFTER_DESCENDANTS
//    this.isFocusable = true
//    this.isFocusableInTouchMode = true
//}
//
//fun CategoryDetails.toBookmark(): MovieBookmark {
//    return MovieBookmark(
//        id = this.content.id,
//        title = this.content.name,
//        image = this.content.image ?: "",
//        categoryProperty = this.content.categoryProperty,
//        categoryid = this.content.categoryid,
//        country = this.content.country,
//        description = this.content.description,
//        language = this.content.language,
//        rating = this.content.rating,
//        release_year = this.content.release_year
//    )
//}

@SuppressLint("HardwareIds")
fun getAndroidId(context: Context): String {
    return Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    )
}

inline fun <reified T> String.toDataClass(): T {
    val gson = Gson()
    return gson.fromJson(this, T::class.java)
}

fun String.toYear(): String {
    return this.substring(0, 4)
}