package com.easyradio.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Shared by [notifications.NewEpisodeCheckWorker]'s auto-download gate and the manual-download cellular warning. */
object NetworkUtils {
    fun isOnWifi(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
