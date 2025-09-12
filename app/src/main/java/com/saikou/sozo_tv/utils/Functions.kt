package com.saikou.sozo_tv.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.adapters.CharactersPageAdapter
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
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
fun VerticalGridView.setupGridLayoutForCategories(pageAdapter: CategoriesPageAdapter) {
    this.apply {
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        isFocusDrawingOrderEnabled = true

        // Dynamic columns: each item ~180dp wide
        val columnCount = calculateDynamicColumns(180)
        setNumColumns(columnCount)
    }
}

fun VerticalGridView.setupGridLayoutForBookmarks() {
    this.apply {
        isFocusable = true
        isFocusableInTouchMode = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        isFocusDrawingOrderEnabled = true
        val columnCount = calculateDynamicColumns(200)
        setNumColumns(columnCount)
    }
}

/**
 * Calculates number of columns based on screen width and item width in dp.
 */
private fun VerticalGridView.calculateDynamicColumns(itemWidthDp: Int): Int {
    val displayMetrics = context.resources.displayMetrics
    val screenWidthPx = displayMetrics.widthPixels
    val itemWidthPx = (itemWidthDp * displayMetrics.density).toInt()
    val calculated = screenWidthPx / itemWidthPx

    return if (calculated < 4) 4 else calculated
}




fun String.toDateFromIso8601ForTxt(): String? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val instant = LocalDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME)
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
            instant.format(formatter)
        } else {
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
        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}


fun <T> readData(fileName: String, context: Context? = null, toast: Boolean = true): T? {
    val a = context ?: MyApp.context
    try {
        if (a.fileList() != null)
            if (fileName in a.fileList()) {
                val fileIS: FileInputStream = a.openFileInput(fileName)
                val objIS = ObjectInputStream(fileIS)
                val data = objIS.readObject() as T
                objIS.close()
                fileIS.close()
                return data
            }
    } catch (e: Exception) {
        if (toast) snackString("Error loading data $fileName")
        e.printStackTrace()
    }
    return null
}

fun <T> tryWith(post: Boolean = false, snackbar: Boolean = true, call: () -> T): T? {
    return try {
        call.invoke()
    } catch (e: Throwable) {
        null
    }
}

fun saveData(fileName: String, data: Any?, context: Context? = null) {
    tryWith {
        val a = context ?: MyApp.context
        if (a != null) {
            val fos: FileOutputStream = a.openFileOutput(fileName, Context.MODE_PRIVATE)
            val os = ObjectOutputStream(fos)
            os.writeObject(data)
            os.close()
            fos.close()
        }
    }
}
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

inline fun <T> LiveData<T>.observeOnce(
    lifecycleOwner: LifecycleOwner,
    crossinline observer: (T) -> Unit
) {
    val internalObserver = object : Observer<T> {
        override fun onChanged(value: T) {
            observer(value)
            removeObserver(this)
        }
    }
    observe(lifecycleOwner, internalObserver)
}
