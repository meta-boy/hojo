package com.anurag4884.hojo.epaperconnectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import com.facebook.react.modules.network.OkHttpClientProvider
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@RequiresApi(Build.VERSION_CODES.Q)
class EpaperConnectivityModule : Module() {

    companion object {
        private const val EPAPER_SSID = "E-Paper"
        private const val EPAPER_PASSWORD = "12345678"
        private const val EPAPER_IP = "192.168.3.3"
        private const val EPAPER_PORT = 80
        private const val SOCKET_TIMEOUT_MS = 5000
    }

    // Helper to access the Android Context safely
    private val connectivityManager: ConnectivityManager
        get() =
                appContext.reactContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as
                        ConnectivityManager

    private var epaperNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Defines the module structure for Expo
    override fun definition() = ModuleDefinition {
        Name("EpaperConnectivityModule")

        OnCreate {
            try {
                OkHttpClientProvider.setOkHttpClientFactory {
                    OkHttpClient.Builder()
                            .cookieJar(com.facebook.react.modules.network.ReactCookieJarContainer())
                            .addInterceptor(SmartNetworkInterceptor(connectivityManager))
                            .build()
                }
            } catch (e: Exception) {
                android.util.Log.e("EpaperConnectivity", "Failed to register OkHttp factory", e)
            }
        }

        // Phase 2: Core Connection
        AsyncFunction("connectToEpaperHotspot") { promise: Promise ->
            try {
                val wifiNetworkSpecifier =
                        WifiNetworkSpecifier.Builder()
                                .setSsid(EPAPER_SSID)
                                .setWpa2Passphrase(EPAPER_PASSWORD)
                                .build()

                val networkRequest =
                        NetworkRequest.Builder()
                                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                .setNetworkSpecifier(wifiNetworkSpecifier)
                                .build()

                networkCallback =
                        object : ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: Network) {
                                super.onAvailable(network)
                                epaperNetwork = network
                                promise.resolve(true)
                            }

                            override fun onUnavailable() {
                                super.onUnavailable()
                                promise.reject(
                                        "CONNECTION_FAILED",
                                        "Failed to connect to E-Paper hotspot: Network unavailable",
                                        null
                                )
                            }

                            override fun onLost(network: Network) {
                                super.onLost(network)
                                if (epaperNetwork == network) {
                                    epaperNetwork = null
                                }
                            }
                        }

                connectivityManager.requestNetwork(networkRequest, networkCallback!!)
            } catch (e: Exception) {
                promise.reject(
                        "CONNECTION_ERROR",
                        "Error connecting to E-Paper hotspot: ${e.message}",
                        e
                )
            }
        }

        // Phase 3: Bind Process
        AsyncFunction("bindToEpaperNetwork") { promise: Promise ->
            try {
                val network = epaperNetwork
                if (network == null) {
                    promise.reject(
                            "NO_NETWORK",
                            "E-Paper network not available. Connect first.",
                            null
                    )
                    return@AsyncFunction
                }

                val success = connectivityManager.bindProcessToNetwork(network)
                if (success) {
                    promise.resolve(true)
                } else {
                    promise.reject("BIND_FAILED", "Failed to bind to E-Paper network", null)
                }
            } catch (e: Exception) {
                promise.reject("BIND_ERROR", "Error binding to E-Paper network: ${e.message}", e)
            }
        }

        // Phase 3: Unbind
        AsyncFunction("unbindNetwork") {
            try {
                connectivityManager.bindProcessToNetwork(null)
            } catch (e: Exception) {
                android.util.Log.e("EpaperConnectivity", "Error unbinding network: ${e.message}")
            }
        }

        // Phase 4: Handshake
        AsyncFunction("testEpaperConnection") { promise: Promise ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val socket = Socket()
                    val socketAddress = InetSocketAddress(EPAPER_IP, EPAPER_PORT)
                    socket.connect(socketAddress, SOCKET_TIMEOUT_MS)

                    val isConnected = socket.isConnected
                    socket.close()

                    withContext(Dispatchers.Main) { promise.resolve(isConnected) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { promise.resolve(false) }
                }
            }
        }

        // Cleanup
        AsyncFunction("disconnectEpaperHotspot") {
            try {
                connectivityManager.bindProcessToNetwork(null)

                networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }

                networkCallback = null
                epaperNetwork = null
            } catch (e: Exception) {
                android.util.Log.e("EpaperConnectivity", "Error disconnecting: ${e.message}")
            }
        }

        AsyncFunction("isConnected") {
            return@AsyncFunction (epaperNetwork != null)
        }

        // Get Info
        AsyncFunction("getNetworkInfo") { promise: Promise ->
            try {
                val network = epaperNetwork
                if (network == null) {
                    promise.resolve(null)
                    return@AsyncFunction
                }

                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val linkProperties = connectivityManager.getLinkProperties(network)

                // Expo automatically converts Kotlin Maps to JS Objects!
                val info =
                        mapOf(
                                "connected" to true,
                                "ssid" to EPAPER_SSID,
                                "targetIp" to EPAPER_IP,
                                "targetPort" to EPAPER_PORT,
                                "hasWifi" to
                                        (capabilities?.hasTransport(
                                                NetworkCapabilities.TRANSPORT_WIFI
                                        )
                                                ?: false),
                                "hasInternet" to
                                        (capabilities?.hasCapability(
                                                NetworkCapabilities.NET_CAPABILITY_INTERNET
                                        )
                                                ?: false),
                                "interfaceName" to linkProperties?.interfaceName
                        )

                promise.resolve(info)
            } catch (e: Exception) {
                promise.reject("INFO_ERROR", "Error getting network info: ${e.message}", e)
            }
        }
    }
}
