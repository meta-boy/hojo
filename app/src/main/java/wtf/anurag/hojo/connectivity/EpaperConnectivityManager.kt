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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
@RequiresApi(Build.VERSION_CODES.Q)
class EpaperConnectivityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val EPAPER_SSID = "E-Paper"
        private const val EPAPER_PASSWORD = "12345678"
        private const val EPAPER_IP = "192.168.3.3"
        private const val EPAPER_PORT = 80
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val PING_TIMEOUT_MS = 3000
        private const val DISCOVERY_TIMEOUT_MS = 1000
        private const val MAX_PING_RETRIES = 5
        private const val RETRY_DELAY_MS = 500L
        private const val TAG = "EpaperConnectivity"
    }

    private val isEmulator: Boolean
        get() =
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                        Build.FINGERPRINT.startsWith("generic") ||
                        Build.FINGERPRINT.startsWith("unknown") ||
                        Build.HARDWARE.contains("goldfish") ||
                        Build.HARDWARE.contains("ranchu") ||
                        Build.MODEL.contains("google_sdk") ||
                        Build.MODEL.contains("Emulator") ||
                        Build.MODEL.contains("Android SDK built for x86") ||
                        Build.MANUFACTURER.contains("Genymotion") ||
                        Build.PRODUCT.contains("sdk_google") ||
                        Build.PRODUCT.contains("google_sdk") ||
                        Build.PRODUCT.contains("sdk") ||
                        Build.PRODUCT.contains("sdk_x86") ||
                        Build.PRODUCT.contains("vbox86p") ||
                        Build.PRODUCT.contains("emulator") ||
                        Build.PRODUCT.contains("simulator")

    private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var epaperNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _discoveredDeviceIp = MutableStateFlow<String?>(null)
    val discoveredDeviceIp = _discoveredDeviceIp.asStateFlow()

    suspend fun connectToEpaperHotspot(activity: Activity? = null): Boolean =
            withContext(Dispatchers.IO) {
                if (isEmulator) {
                    Log.d(TAG, "connectToEpaperHotspot -> skipping on emulator")
                    return@withContext false
                }

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
                if (isEmulator) {
                    Log.d(TAG, "bindToEpaperNetwork -> skipping on emulator")
                    return@withContext false
                }
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
                if (isEmulator) {
                    Log.d(TAG, "testEpaperConnection -> skipping on emulator")
                    return@withContext false
                }
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

    fun getNetworkInfo(): Map<String, Any?> {
        val network = epaperNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val linkProperties = network?.let { connectivityManager.getLinkProperties(it) }

        return mapOf(
                "connected" to (network != null),
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

    /**
     * Get the base URL for the e-paper device. Returns discovered IP if available, otherwise falls
     * back to hardcoded IP.
     */
    fun getDeviceBaseUrl(): String {
        val ip = _discoveredDeviceIp.value ?: EPAPER_IP
        return "http://$ip"
    }

    private val nsdManager =
            context.getSystemService(Context.NSD_SERVICE) as android.net.nsd.NsdManager
    private var discoveryListener: android.net.nsd.NsdManager.DiscoveryListener? = null
    private val SERVICE_TYPE = "_http._tcp."

    /**
     * Discover e-paper device on the local network using mDNS (NsdManager). Looks for services of
     * type _http._tcp.
     */
    @Suppress("DEPRECATION")
    suspend fun discoverDeviceOnNetwork(): String? =
            withContext(Dispatchers.IO) {
                Log.d(TAG, "discoverDeviceOnNetwork -> starting mDNS discovery")
                _discoveredDeviceIp.value = null

                return@withContext suspendCancellableCoroutine { continuation ->
                    val listener =
                            object : android.net.nsd.NsdManager.DiscoveryListener {
                                override fun onDiscoveryStarted(serviceType: String) {
                                    Log.d(TAG, "Service discovery started")
                                }

                                override fun onServiceFound(
                                        serviceInfo: android.net.nsd.NsdServiceInfo
                                ) {
                                    Log.d(TAG, "Service found: $serviceInfo")
                                    if (serviceInfo.serviceType == SERVICE_TYPE ||
                                                    serviceInfo.serviceType == "$SERVICE_TYPE."
                                    ) {
                                        nsdManager.resolveService(
                                                serviceInfo,
                                                object :
                                                        android.net.nsd.NsdManager.ResolveListener {
                                                    override fun onResolveFailed(
                                                            serviceInfo:
                                                                    android.net.nsd.NsdServiceInfo,
                                                            errorCode: Int
                                                    ) {
                                                        Log.e(TAG, "Resolve failed: $errorCode")
                                                    }

                                                    override fun onServiceResolved(
                                                            serviceInfo:
                                                                    android.net.nsd.NsdServiceInfo
                                                    ) {
                                                        Log.d(TAG, "Service resolved: $serviceInfo")
                                                        val host = serviceInfo.host
                                                        if (host != null) {
                                                            val ip = host.hostAddress
                                                            Log.d(TAG, "Resolved IP: $ip")
                                                            if (ip != null && ip != "0.0.0.0") {
                                                                if (continuation.isActive) {
                                                                    _discoveredDeviceIp.value = ip
                                                                    continuation.resume(ip)
                                                                    stopDiscovery()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                        )
                                    }
                                }

                                override fun onServiceLost(
                                        serviceInfo: android.net.nsd.NsdServiceInfo
                                ) {
                                    Log.e(TAG, "service lost: $serviceInfo")
                                }

                                override fun onDiscoveryStopped(serviceType: String) {
                                    Log.i(TAG, "Discovery stopped: $serviceType")
                                }

                                override fun onStartDiscoveryFailed(
                                        serviceType: String,
                                        errorCode: Int
                                ) {
                                    Log.e(TAG, "Discovery failed: Error code:$errorCode")
                                    nsdManager.stopServiceDiscovery(this)
                                }

                                override fun onStopDiscoveryFailed(
                                        serviceType: String,
                                        errorCode: Int
                                ) {
                                    Log.e(TAG, "Discovery failed: Error code:$errorCode")
                                    nsdManager.stopServiceDiscovery(this)
                                }
                            }

                    discoveryListener = listener
                    try {
                        nsdManager.discoverServices(
                                SERVICE_TYPE,
                                android.net.nsd.NsdManager.PROTOCOL_DNS_SD,
                                listener
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start discovery", e)
                        if (continuation.isActive) continuation.resume(null)
                    }

                    // Timeout for discovery
                    @Suppress("OPT_IN_USAGE")
                    GlobalScope.launch {
                        kotlinx.coroutines.delay(5000) // 5 seconds timeout
                        if (continuation.isActive) {
                            Log.d(TAG, "Discovery timed out")
                            stopDiscovery()
                            continuation.resume(null)
                        }
                    }
                }
            }

    private fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop discovery", e)
            }
            discoveryListener = null
        }
    }
}
