package com.saikou.sozo_tv.utils

import android.app.ActionBar.LayoutParams
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import com.saikou.sozo_tv.R

object DialogUtils {

    fun loadingDialog(ctx: Context): Dialog {
        val dialog = Dialog(ctx)
        dialog.setContentView(R.layout.dialog_loading)
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }
}