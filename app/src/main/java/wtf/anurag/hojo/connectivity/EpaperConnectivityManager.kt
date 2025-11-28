package wtf.anurag.hojo.connectivity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.Q)
class EpaperConnectivityManager(private val context: Context) {

    companion object {
        private const val EPAPER_SSID = "E-Paper"
        private const val EPAPER_PASSWORD = "12345678"
        private const val EPAPER_IP = "192.168.3.3"
        private const val EPAPER_PORT = 80
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val PING_TIMEOUT_MS = 3000
        private const val MAX_PING_RETRIES = 5
        private const val RETRY_DELAY_MS = 500L
        private const val TAG = "EpaperConnectivity"
    }

    private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var epaperNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    suspend fun connectToEpaperHotspot(activity: Activity? = null): Boolean =
            withContext(Dispatchers.IO) {
                // Request permissions if activity is provided
                if (activity != null) {
                    val permissions = mutableListOf<String>()
                    if (ActivityCompat.checkSelfPermission(
                                    activity,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    if (Build.VERSION.SDK_INT >= 33) {
                        if (ActivityCompat.checkSelfPermission(
                                        activity,
                                        "android.permission.NEARBY_WIFI_DEVICES"
                                ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissions.add("android.permission.NEARBY_WIFI_DEVICES")
                        }
                    }
                    if (permissions.isNotEmpty()) {
                        // Note: This is a synchronous check, actual request needs to be handled in
                        // Activity result or via ActivityResultLauncher
                        // For now, we assume permissions are handled or we request them and fail if
                        // not granted immediately (simplified)
                        // In a real app, use Accompanist Permissions or ActivityResultLauncher
                        withContext(Dispatchers.Main) {
                            ActivityCompat.requestPermissions(
                                    activity,
                                    permissions.toTypedArray(),
                                    1001
                            )
                        }
                        // We can't easily wait for result here without callback, so we proceed and
                        // let system handle it
                    }
                }

                return@withContext suspendCancellableCoroutine { continuation ->
                    try {
                        val wifiNetworkSpecifier =
                                WifiNetworkSpecifier.Builder()
                                        .setSsid(EPAPER_SSID)
                                        .setWpa2Passphrase(EPAPER_PASSWORD)
                                        .build()

                        val networkRequest =
                                NetworkRequest.Builder()
                                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                                        .removeCapability(
                                                NetworkCapabilities.NET_CAPABILITY_INTERNET
                                        )
                                        .setNetworkSpecifier(wifiNetworkSpecifier)
                                        .build()

                        networkCallback =
                                object : ConnectivityManager.NetworkCallback() {
                                    override fun onAvailable(network: Network) {
                                        super.onAvailable(network)
                                        epaperNetwork = network
                                        _isConnected.value = true
                                        Log.d(
                                                TAG,
                                                "onAvailable: epaper network available: $network"
                                        )
                                        if (continuation.isActive) continuation.resume(true)
                                    }

                                    override fun onUnavailable() {
                                        super.onUnavailable()
                                        Log.d(TAG, "onUnavailable: failed to find epaper network")
                                        if (continuation.isActive) continuation.resume(false)
                                    }

                                    override fun onLost(network: Network) {
                                        super.onLost(network)
                                        Log.d(TAG, "onLost: network lost: $network")
                                        if (epaperNetwork == network) {
                                            epaperNetwork = null
                                            _isConnected.value = false
                                        }
                                    }
                                }

                        connectivityManager.requestNetwork(networkRequest, networkCallback!!)
                    } catch (e: Exception) {
                        Log.e(TAG, "connectToEpaperHotspot -> exception", e)
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            }

    suspend fun bindToEpaperNetwork(): Boolean =
            withContext(Dispatchers.IO) {
                val network =
                        epaperNetwork
                                ?: run {
                                    Log.d(TAG, "bindToEpaperNetwork -> no epaperNetwork to bind to")
                                    return@withContext false
                                }
                val result = connectivityManager.bindProcessToNetwork(network)
                Log.d(TAG, "bindToEpaperNetwork -> bind result: $result")

                if (result) {
                    // Wait for e-paper device to be ready
                    waitForEpaperReady()
                }

                return@withContext result
            }

    suspend fun unbindNetwork() =
            withContext(Dispatchers.IO) {
                connectivityManager.bindProcessToNetwork(null)
                Log.d(TAG, "unbindNetwork -> unbound from network")

                // Wait for internet to be ready
                waitForInternetReady()
            }

    suspend fun testEpaperConnection(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    val socketAddress = InetSocketAddress(EPAPER_IP, EPAPER_PORT)
                    socket.connect(socketAddress, SOCKET_TIMEOUT_MS)
                    val connected = socket.isConnected
                    socket.close()
                    Log.d(TAG, "testEpaperConnection -> connected=$connected")
                    connected
                } catch (e: Exception) {
                    Log.w(TAG, "testEpaperConnection -> failed", e)
                    false
                }
            }

    private suspend fun waitForInternetReady() {
        Log.d(TAG, "waitForInternetReady -> checking internet connectivity")
        var retries = 0
        while (retries < MAX_PING_RETRIES) {
            if (pingHost("1.1.1.1", 80)) {
                Log.d(TAG, "waitForInternetReady -> internet is ready")
                return
            }
            retries++
            if (retries < MAX_PING_RETRIES) {
                Log.d(TAG, "waitForInternetReady -> retry $retries/$MAX_PING_RETRIES")
                kotlinx.coroutines.delay(RETRY_DELAY_MS * retries) // Exponential backoff
            }
        }
        Log.w(TAG, "waitForInternetReady -> failed after $MAX_PING_RETRIES retries")
    }

    private suspend fun waitForEpaperReady() {
        Log.d(TAG, "waitForEpaperReady -> checking e-paper connectivity")
        var retries = 0
        while (retries < MAX_PING_RETRIES) {
            if (pingEpaperStatus()) {
                Log.d(TAG, "waitForEpaperReady -> e-paper is ready")
                return
            }
            retries++
            if (retries < MAX_PING_RETRIES) {
                Log.d(TAG, "waitForEpaperReady -> retry $retries/$MAX_PING_RETRIES")
                kotlinx.coroutines.delay(RETRY_DELAY_MS * retries) // Exponential backoff
            }
        }
        Log.w(TAG, "waitForEpaperReady -> failed after $MAX_PING_RETRIES retries")
    }

    private suspend fun pingHost(host: String, port: Int): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    val socketAddress = InetSocketAddress(host, port)
                    socket.connect(socketAddress, PING_TIMEOUT_MS)
                    val connected = socket.isConnected
                    socket.close()
                    Log.d(TAG, "pingHost -> $host:$port connected=$connected")
                    connected
                } catch (e: Exception) {
                    Log.d(TAG, "pingHost -> $host:$port failed: ${e.message}")
                    false
                }
            }

    private suspend fun pingEpaperStatus(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL("http://$EPAPER_IP/status")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = PING_TIMEOUT_MS
                    connection.readTimeout = PING_TIMEOUT_MS
                    connection.requestMethod = "GET"
                    val responseCode = connection.responseCode
                    connection.disconnect()
                    val success = responseCode == 200
                    Log.d(TAG, "pingEpaperStatus -> responseCode=$responseCode success=$success")
                    success
                } catch (e: Exception) {
                    Log.d(TAG, "pingEpaperStatus -> failed: ${e.message}")
                    false
                }
            }

    fun disconnectEpaperHotspot() {
        connectivityManager.bindProcessToNetwork(null)
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        networkCallback = null
        epaperNetwork = null
        _isConnected.value = false
    }

    fun getNetworkInfo(): Map<String, Any?>? {
        val network = epaperNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val linkProperties = connectivityManager.getLinkProperties(network)

        return mapOf(
                "connected" to true,
                "ssid" to EPAPER_SSID,
                "targetIp" to EPAPER_IP,
                "targetPort" to EPAPER_PORT,
                "hasWifi" to
                        (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true),
                "hasInternet" to
                        (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ==
                                true),
                "interfaceName" to linkProperties?.interfaceName
        )
    }
}
