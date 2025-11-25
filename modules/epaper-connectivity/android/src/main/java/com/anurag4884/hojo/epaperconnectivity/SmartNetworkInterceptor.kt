package com.anurag4884.hojo.epaperconnectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import expo.modules.kotlin.AppContext
import okhttp3.Interceptor
import okhttp3.Response

@RequiresApi(Build.VERSION_CODES.M)
class SmartNetworkInterceptor(private val appContext: AppContext) : Interceptor {

    private val connectivityManager: ConnectivityManager?
        get() =
                appContext.reactContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as?
                        ConnectivityManager

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // 1. Identify the Need to Switch
        // If it's a local request, just proceed.
        if (url.contains("192.168.3.3")) {
            return chain.proceed(request)
        }

        val cm = connectivityManager
        if (cm == null) {
            // Context not ready, proceed normally
            return chain.proceed(request)
        }

        // 2. Check Current Network State
        val boundNetwork = cm.boundNetworkForProcess

        if (boundNetwork == null) {
            // No binding, proceed normally
            return chain.proceed(request)
        }

        // Check if the bound network is the E-Paper network (WiFi and !Internet)
        val caps = cm.getNetworkCapabilities(boundNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (isWifi && !hasInternet) {
            // 3. The Forced Network Switch
            return switchNetwork(chain, boundNetwork, cm)
        }

        // Bound to a good network (Cellular or Validated WiFi), proceed
        return chain.proceed(request)
    }

    private fun switchNetwork(
            chain: Interceptor.Chain,
            originalNetwork: Network,
            cm: ConnectivityManager
    ): Response {
        try {
            // 1. Unbind
            cm.bindProcessToNetwork(null)

            // 2. Wait for System Reroute
            try {
                Thread.sleep(500L)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            // 3. Execute Internet Request
            return chain.proceed(chain.request())
        } finally {
            // 4. Restore Binding
            cm.bindProcessToNetwork(originalNetwork)
        }
    }
}
