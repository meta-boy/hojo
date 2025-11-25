package com.anurag4884.hojo.epaperconnectivity

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import okhttp3.Interceptor
import okhttp3.Response

@RequiresApi(Build.VERSION_CODES.M)
class SmartNetworkInterceptor(private val connectivityManager: ConnectivityManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        // 1. Identify the Need to Switch
        // If it's a local request, just proceed.
        if (url.contains("192.168.3.3")) {
            return chain.proceed(request)
        }

        // 2. Check Current Network State
        val boundNetwork = connectivityManager.boundNetworkForProcess

        if (boundNetwork == null) {
            // No binding, proceed normally
            return chain.proceed(request)
        }

        // Check if the bound network is the E-Paper network (WiFi and !Internet)
        val caps = connectivityManager.getNetworkCapabilities(boundNetwork)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (isWifi && !hasInternet) {
            // 3. The Forced Network Switch
            return switchNetwork(chain, boundNetwork)
        }

        // Bound to a good network (Cellular or Validated WiFi), proceed
        return chain.proceed(request)
    }

    private fun switchNetwork(chain: Interceptor.Chain, originalNetwork: Network): Response {
        try {
            // 1. Unbind
            connectivityManager.bindProcessToNetwork(null)

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
            connectivityManager.bindProcessToNetwork(originalNetwork)
        }
    }
}
