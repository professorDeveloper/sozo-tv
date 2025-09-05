package com.saikou.sozo_tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.saikou.sozo_tv.presentation.activities.NoInternetActivity

class NetworkChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!isNetworkAvailable(context)) {
            val i = Intent(context, NoInternetActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            context.startActivity(i)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
