package com.saikou.sozo_tv.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
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
import com.saikou.sozo_tv.R
import com.saikou.sozo_tv.app.MyApp
import com.saikou.sozo_tv.domain.exceptions.ApiException
import com.saikou.sozo_tv.domain.exceptions.NotFoundException
import com.saikou.sozo_tv.domain.exceptions.ServerException
import com.saikou.sozo_tv.presentation.screens.category.CategoriesPageAdapter
import retrofit2.Response
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

fun <T> Response<T>.toResult(): Result<T> {
    return when {
        this.isSuccessful && this.body() != null -> {
            Result.success(this.body()!!)
        }

        this.code() == 404 -> {
            Result.failure(NotFoundException("Resource not found: ${this.message()}"))
        }

        this.code() in 500..599 -> {
            Result.failure(ServerException("Server error (${this.code()}): ${this.message()}"))
        }

        else -> {
            Result.failure(
                ApiException(
                    "API error (${this.code()}): ${
                        this.errorBody()?.string()
                    }"
                )
            )
        }
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
                    shapeDrawable.paint.color = activity.getColor(R.color.main_background)// Set the background color if needed
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


fun View.visible() {
    this.visibility = View.VISIBLE
}


fun View.gone() {
    this.visibility = View.GONE
}

fun <T> tryWith(post: Boolean = false, snackbar: Boolean = true, call: () -> T): T? {
    return try {
        call.invoke()
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

fun <T : Serializable> readData(
    fileName: String,
    context: Context? = null,
    toast: Boolean = true
): T? {
    val a = context ?: MyApp.context

    try {
        val files = a.fileList()
        if (files != null && fileName in files) {
            val fileIS = a.openFileInput(fileName)
            val objIS = ObjectInputStream(fileIS)

            @Suppress("UNCHECKED_CAST")
            val data = objIS.readObject() as T
            objIS.close()
            fileIS.close()
            return data
        }
    } catch (e: Exception) {
        if (toast) snackString("Error loading data $fileName")
        e.printStackTrace()

        try {
            a.deleteFile(fileName)
        } catch (_: Exception) {
        }
    }
    return null
}

fun <T : Serializable> saveData(
    fileName: String,
    data: T?,
    context: Context? = null
) {
    if (data == null) return

    tryWith {
        val a = context ?: MyApp.context
        val fos: FileOutputStream = a.openFileOutput(fileName, Context.MODE_PRIVATE)
        val os = ObjectOutputStream(fos)
        os.writeObject(data)
        os.close()
        fos.close()
        null
    }
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
