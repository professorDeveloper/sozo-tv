package com.saikou.sozo_tv.presentation.screens.play.dialog

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.saikou.sozo_tv.R

fun Dialog.applyGlassWindow(gravity: Int = Gravity.CENTER, blurRadius: Int = 48) {
    val w = window ?: return
    w.setBackgroundDrawable(ColorDrawable(0))
    w.setWindowAnimations(R.style.DialogAnimation)
    w.setGravity(gravity)
    w.setLayout(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
    )
    val blurred = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    if (blurred) {
        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        w.blurBehind(blurRadius)
    } else {
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        w.setDimAmount(0.45f)
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Window.blurBehind(radius: Int) {
    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    attributes = attributes.apply { blurBehindRadius = radius }
}
