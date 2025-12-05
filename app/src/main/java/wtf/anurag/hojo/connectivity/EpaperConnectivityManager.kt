package wtf.anurag.hojo.connectivity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Enhanced E-Paper Connectivity Manager
 *
 * Features derived from reverse-engineering the XTPlus E-Paper app:
 * - Multi-method device discovery (Socket mDNS, JmDNS, NSD)
 * - Smart network selection for dual-network scenarios
 * - LAN mode vs Hotspot mode switching
 * - Internet network caching and validation
 * - Custom DNS resolution for device communication
 */
@Singleton
@RequiresApi(Build.VERSION_CODES.Q)
class EpaperConnectivityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "EpaperConnectivity"

        // Device Configuration
        private const val EPAPER_SSID = "E-Paper"
        private const val EPAPER_PASSWORD = "12345678"
        private const val EPAPER_IP = "192.168.3.3"
        private const val EPAPER_PORT = 80
        private const val DEVICE_HOST_NAME = "e-paper"
        private const val DEVICE_LAN_HOST = "e-paper.local"

        // Network Segments (for detecting E-Paper WiFi)
        private const val WIFI_NETWORK_SEGMENT = "192.168.3."
        private val DEVICE_IP_PREFIXES = listOf("192.168.3.", "192.168.4.", "10.0.0.")

        // Timeouts
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val PING_TIMEOUT_MS = 3000
        private const val HTTP_TIMEOUT_MS = 8000L
        private const val PROBE_TIMEOUT_MS = 5000L
        private const val RESOLVE_TIMEOUT_MS = 5000L
        private const val DISCOVERY_TIMEOUT_MS = 1000
        private const val HOTSPOT_DISCOVERY_TIMEOUT_MS = 30000L

        // Retry Configuration
        private const val MAX_PING_RETRIES = 5
        private const val RETRY_DELAY_MS = 500L
        private const val DETECT_MAX_ATTEMPTS = 2
        private const val DETECT_RETRY_DELAY_MS = 1500L

        // Internet Network Cache Duration
        private const val NETWORK_CACHE_DURATION_MS = 10000L
    }

    // ========================
    // Connection Mode Tracking
    // ========================

    enum class ConnectionMode {
        LAN,      // Connected via phone's hotspot, device discovered on local network
        HOTSPOT   // Connected directly to device's WiFi hotspot
    }

    private val _connectionMode = MutableStateFlow(ConnectionMode.HOTSPOT)
    val connectionMode = _connectionMode.asStateFlow()

    private val _currentBaseUrl = MutableStateFlow("http://$EPAPER_IP")
    val currentBaseUrl = _currentBaseUrl.asStateFlow()

    private val _lastResolvedIp = MutableStateFlow<String?>(null)
    val lastResolvedIp = _lastResolvedIp.asStateFlow()

    // ========================
    // Emulator Detection
    // ========================

    private val isEmulator: Boolean
        get() = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
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

    // ========================
    // System Services
    // ========================

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val nsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    // ========================
    // Network State
    // ========================

    private var epaperNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Internet network caching (from NetworkManager)
    private var cachedInternetNetwork: Network? = null
    private var cachedInternetClient: OkHttpClient? = null
    private var networkCacheTime: Long = 0

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _discoveredDeviceIp = MutableStateFlow<String?>(null)
    val discoveredDeviceIp = _discoveredDeviceIp.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering = _isDiscovering.asStateFlow()

    // Probe client for HTTP reachability checks
    private val probeClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    // ========================
    // Hotspot Management
    // ========================

    /**
     * Check if the phone's WiFi hotspot is currently enabled using reflection.
     */
    @Suppress("DEPRECATION")
    private fun isHotspotEnabled(): Boolean {
        return try {
            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            method.invoke(wifiManager) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check hotspot status", e)
            false
        }
    }

    /**
     * Turn off the phone's WiFi hotspot using reflection.
     */
    @Suppress("DEPRECATION")
    private fun disableHotspot(): Boolean {
        return try {
            val method = wifiManager.javaClass.getDeclaredMethod(
                "setWifiApEnabled",
                android.net.wifi.WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(wifiManager, null, false) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable hotspot", e)
            false
        }
    }

    // ========================
    // Smart Network Detection (from SmartNetworkSelector)
    // ========================

    /**
     * Check if a URL points to the local device
     */
    fun isDeviceApi(url: String): Boolean {
        return DEVICE_IP_PREFIXES.any { url.contains(it, ignoreCase = true) } ||
                url.contains(DEVICE_LAN_HOST, ignoreCase = true) ||
                url.contains(DEVICE_HOST_NAME, ignoreCase = true)
    }

    /**
     * Check if current network has internet capability
     */
    fun isCurrentNetworkInternetCapable(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            Log.d(TAG, "Network capabilities: hasInternet=$hasInternet, validated=$isValidated")
            hasInternet && isValidated
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network capabilities", e)
            false
        }
    }

    /**
     * Check if currently connected to the E-Paper device hotspot
     */
    fun isCurrentNetworkDeviceHotspot(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

            // Must be WiFi
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Log.d(TAG, "Not connected to WiFi")
                return false
            }

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            // E-Paper hotspot has internet capability but is not validated
            if (hasInternet && isValidated) {
                Log.d(TAG, "WiFi has internet access - not E-Paper hotspot")
                return false
            }

            // Check IP address range
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
            linkProperties?.linkAddresses?.forEach { linkAddress ->
                val hostAddress = linkAddress.address.hostAddress ?: return@forEach
                if (DEVICE_IP_PREFIXES.any { hostAddress.startsWith(it) }) {
                    Log.d(TAG, "Connected to E-Paper hotspot: $hostAddress")
                    return true
                }
            }

            Log.d(TAG, "WiFi IP address doesn't match E-Paper range")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device hotspot", e)
            false
        }
    }

    /**
     * Check if a specific network is the E-Paper WiFi (from NetworkManager)
     */
    private fun isEPaperWiFi(network: Network): Boolean {
        return try {
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            // E-Paper WiFi: is WiFi + has internet capability + NOT validated
            if (isWifi && hasInternet && !isValidated) {
                Log.d(TAG, "Detected E-Paper WiFi (WiFi+INTERNET, no VALIDATED)")

                val linkProperties = connectivityManager.getLinkProperties(network)
                linkProperties?.linkAddresses?.forEach { linkAddress ->
                    val hostAddress = linkAddress.address.hostAddress ?: return@forEach
                    if (hostAddress.startsWith(WIFI_NETWORK_SEGMENT)) {
                        Log.d(TAG, "Confirmed E-Paper WiFi (IP: $hostAddress)")
                    }
                }
                return true
            }

            // Also check by IP range even if validated
            val linkProperties = connectivityManager.getLinkProperties(network)
            linkProperties?.linkAddresses?.forEach { linkAddress ->
                val hostAddress = linkAddress.address.hostAddress ?: return@forEach
                if (hostAddress.startsWith(WIFI_NETWORK_SEGMENT)) {
                    Log.d(TAG, "Detected E-Paper WiFi by IP range: $hostAddress")
                    return true
                }
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking E-Paper WiFi", e)
            false
        }
    }

    // ========================
    // Network Selection (from SmartNetworkSelector)
    // ========================

    /**
     * Select a network with internet capability
     */
    fun selectInternetNetwork(): Network? {
        return try {
            // First check active network
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (capabilities != null &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    Log.d(TAG, "Active network has internet capability")
                    return activeNetwork
                }
            }

            // Search for cellular network
            val allNetworks = connectivityManager.allNetworks
            for (network in allNetworks) {
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    Log.d(TAG, "Found cellular network with internet")
                    return network
                }
            }

            // Search for validated WiFi (not E-Paper)
            for (network in allNetworks) {
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {

                    // Make sure it's not E-Paper WiFi
                    val linkProperties = connectivityManager.getLinkProperties(network)
                    val isEPaper = linkProperties?.linkAddresses?.any { linkAddress ->
                        val hostAddress = linkAddress.address.hostAddress ?: return@any false
                        DEVICE_IP_PREFIXES.any { hostAddress.startsWith(it) }
                    } ?: false

                    if (!isEPaper) {
                        Log.d(TAG, "Found validated WiFi network")
                        return network
                    }
                }
            }

            Log.w(TAG, "No internet-capable network found")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting internet network", e)
            null
        }
    }

    /**
     * Select the E-Paper device network
     */
    fun selectDeviceNetwork(): Network? {
        return try {
            val allNetworks = connectivityManager.allNetworks
            for (network in allNetworks) {
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue

                val linkProperties = connectivityManager.getLinkProperties(network) ?: continue
                linkProperties.linkAddresses.forEach { linkAddress ->
                    val hostAddress = linkAddress.address.hostAddress ?: return@forEach
                    if (DEVICE_IP_PREFIXES.any { hostAddress.startsWith(it) }) {
                        Log.d(TAG, "Found E-Paper network: $hostAddress")
                        return network
                    }
                }
            }

            Log.w(TAG, "E-Paper network not found")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting device network", e)
            null
        }
    }

    // ========================
    // Main Connection Methods
    // ========================

    /**
     * Scan for the e-paper device on the current network.
     * Returns the discovered IP address or null if not found.
     */
    suspend fun scanForDevice(timeoutMs: Long = PROBE_TIMEOUT_MS): String? = withContext(Dispatchers.IO) {
        if (isEmulator) {
            Log.d(TAG, "scanForDevice -> skipping on emulator")
            return@withContext null
        }

        Log.d(TAG, "scanForDevice -> starting device discovery")
        val result = detectLanEndpoint(timeoutMs)

        if (result is EndpointResult.Success) {
            Log.d(TAG, "scanForDevice -> found device at ${result.resolvedIp}")
            useLanEndpoint(result.resolvedIp)
            _isConnected.value = true
            return@withContext result.resolvedIp
        }

        Log.w(TAG, "scanForDevice -> device not found")
        null
    }

    /**
     * Connect to an already-discovered device using its known IP.
     * Use this when the device has already been found via scanForDevice().
     */
    suspend fun connectToDiscoveredDevice(): Boolean = withContext(Dispatchers.IO) {
        val discoveredIp = _discoveredDeviceIp.value
        if (discoveredIp != null) {
            Log.d(TAG, "connectToDiscoveredDevice -> using known IP: $discoveredIp")
            // Verify the device is still reachable
            if (probeHttpReachability(discoveredIp)) {
                Log.d(TAG, "connectToDiscoveredDevice -> device still reachable at $discoveredIp")
                useLanEndpoint(discoveredIp)
                _isConnected.value = true
                return@withContext true
            }
            Log.w(TAG, "connectToDiscoveredDevice -> device no longer reachable at $discoveredIp")
        }
        false
    }

    /**
     * Main connection method that intelligently decides connection strategy:
     * 1. If device was already discovered, use that IP directly
     * 2. Otherwise try to discover e-paper device on current WiFi network
     * 3. If discovery fails and not on hotspot, connect to e-paper's hotspot
     */
    suspend fun connectToDevice(activity: Activity? = null): Boolean = withContext(Dispatchers.IO) {
        if (isEmulator) {
            Log.d(TAG, "connectToDevice -> skipping on emulator")
            return@withContext false
        }

        // First check if we already have a discovered device IP
        val discoveredIp = _discoveredDeviceIp.value
        if (discoveredIp != null) {
            Log.d(TAG, "connectToDevice -> found previously discovered IP: $discoveredIp")
            if (probeHttpReachability(discoveredIp)) {
                Log.d(TAG, "connectToDevice -> device reachable at $discoveredIp")
                useLanEndpoint(discoveredIp)
                _isConnected.value = true
                return@withContext true
            }
            Log.w(TAG, "connectToDevice -> previously discovered device no longer reachable")
            _discoveredDeviceIp.value = null
        }

        // Check if phone's hotspot is enabled
        val hotspotEnabled = isHotspotEnabled()
        Log.d(TAG, "connectToDevice -> phone hotspot enabled: $hotspotEnabled")

        // Try to discover device on current network
        Log.d(TAG, "connectToDevice -> attempting device discovery on current network")
        val result = detectLanEndpoint()
        if (result is EndpointResult.Success) {
            Log.d(TAG, "connectToDevice -> discovered device at ${result.resolvedIp}")
            useLanEndpoint(result.resolvedIp)
            _isConnected.value = true
            return@withContext true
        }

        Log.d(TAG, "connectToDevice -> device not found on current network")

        // Only turn off hotspot and connect to e-paper's hotspot if explicitly requested
        // For now, if hotspot is enabled, we stay in LAN mode and return false
        if (hotspotEnabled) {
            Log.d(TAG, "connectToDevice -> hotspot is on but device not found, staying in LAN mode")
            return@withContext false
        }

        // Connect to e-paper's hotspot (only when not using phone hotspot)
        Log.d(TAG, "connectToDevice -> connecting to e-paper's hotspot")
        useHotspotEndpoint()
        return@withContext connectToEpaperHotspot(activity)
    }

    /**
     * Force connection to e-paper's WiFi hotspot, disabling phone hotspot if needed.
     * Use this when the user explicitly wants to switch to hotspot mode.
     */
    suspend fun forceConnectToEpaperHotspot(activity: Activity? = null): Boolean = withContext(Dispatchers.IO) {
        if (isEmulator) {
            Log.d(TAG, "forceConnectToEpaperHotspot -> skipping on emulator")
            return@withContext false
        }

        val hotspotEnabled = isHotspotEnabled()
        if (hotspotEnabled) {
            Log.d(TAG, "forceConnectToEpaperHotspot -> turning off phone hotspot")
            if (disableHotspot()) {
                Log.d(TAG, "forceConnectToEpaperHotspot -> hotspot disabled, waiting for WiFi to stabilize")
                delay(2000)
            } else {
                Log.w(TAG, "forceConnectToEpaperHotspot -> failed to disable hotspot")
            }
        }

        useHotspotEndpoint()
        return@withContext connectToEpaperHotspot(activity)
    }

    /**
     * Connect directly to the E-Paper device's WiFi hotspot
     */
    suspend fun connectToEpaperHotspot(activity: Activity? = null): Boolean = withContext(Dispatchers.IO) {
        if (isEmulator) {
            Log.d(TAG, "connectToEpaperHotspot -> skipping on emulator")
            return@withContext false
        }

        // Request permissions if activity is provided
        if (activity != null) {
            requestPermissionsIfNeeded(activity)
        }

        return@withContext suspendCancellableCoroutine { continuation ->
            try {
                val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
                    .setSsid(EPAPER_SSID)
                    .setWpa2Passphrase(EPAPER_PASSWORD)
                    .build()

                val networkRequest = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(wifiNetworkSpecifier)
                    .build()

                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        epaperNetwork = network
                        _isConnected.value = true
                        Log.d(TAG, "onAvailable: epaper network available: $network")
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

    private suspend fun requestPermissionsIfNeeded(activity: Activity) {
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
            withContext(Dispatchers.Main) {
                ActivityCompat.requestPermissions(activity, permissions.toTypedArray(), 1001)
            }
        }
    }

    // ========================
    // Network Binding
    // ========================

    suspend fun bindToEpaperNetwork(): Boolean = withContext(Dispatchers.IO) {
        if (isEmulator) {
            Log.d(TAG, "bindToEpaperNetwork -> skipping on emulator")
            return@withContext false
        }

        val network = epaperNetwork ?: run {
            Log.d(TAG, "bindToEpaperNetwork -> no epaperNetwork to bind to")
            return@withContext false
        }

        val result = connectivityManager.bindProcessToNetwork(network)
        Log.d(TAG, "bindToEpaperNetwork -> bind result: $result")

        if (result) {
            waitForEpaperReady()
        }

        return@withContext result
    }

    suspend fun unbindNetwork() = withContext(Dispatchers.IO) {
        connectivityManager.bindProcessToNetwork(null)
        Log.d(TAG, "unbindNetwork -> unbound from network")
        waitForInternetReady()
    }

    /**
     * Bind to internet network for API requests (from NetworkBindingManager)
     */
    fun bindNetworkForApiRequest(network: Network): Boolean {
        return try {
            connectivityManager.bindProcessToNetwork(network)
            Log.d(TAG, "Bound to network for API request")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind network", e)
            false
        }
    }

    /**
     * Prepare network for API request - ensures internet connectivity
     */
    suspend fun prepareNetworkForApiRequest(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "========== Preparing network for API request ==========")

        if (isCurrentNetworkInternetCapable()) {
            Log.d(TAG, "Current network has internet capability")
            unbindNetwork()
            return@withContext true
        }

        Log.d(TAG, "Current network lacks internet, searching for alternative...")
        val internetNetwork = selectInternetNetwork()

        if (internetNetwork == null) {
            Log.e(TAG, "No internet-capable network found")
            return@withContext false
        }

        Log.d(TAG, "Found internet network, binding...")
        if (bindNetworkForApiRequest(internetNetwork)) {
            Log.d(TAG, "Testing network connectivity...")
            val isConnected = testInternetConnectivity()
            if (isConnected) {
                Log.d(TAG, "Network ready for API requests")
                return@withContext true
            }
            Log.w(TAG, "Network bound but connectivity test failed")
            unbindNetwork()
        }

        Log.e(TAG, "Failed to prepare network for API request")
        return@withContext false
    }

    /**
     * Prepare network for file push to device
     */
    suspend fun prepareNetworkForFilePush(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "========== Preparing network for file push ==========")

        if (isCurrentNetworkDeviceHotspot()) {
            Log.d(TAG, "Already connected to E-Paper hotspot")
            unbindNetwork()
            return@withContext true
        }

        Log.w(TAG, "Not connected to E-Paper hotspot")
        val deviceNetwork = selectDeviceNetwork()

        if (deviceNetwork != null) {
            Log.d(TAG, "Found E-Paper network, binding...")
            unbindNetwork()
            delay(200)

            try {
                connectivityManager.bindProcessToNetwork(deviceNetwork)
                Log.d(TAG, "Bound to E-Paper network")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind to E-Paper network", e)
            }
        }

        Log.w(TAG, "E-Paper network not found")
        return@withContext false
    }

    // ========================
    // Connection Testing
    // ========================

    suspend fun testEpaperConnection(): Boolean = withContext(Dispatchers.IO) {
        if (isEmulator) {
            Log.d(TAG, "testEpaperConnection -> skipping on emulator")
            return@withContext false
        }

        try {
            val ip = _discoveredDeviceIp.value ?: EPAPER_IP
            val socket = Socket()
            val socketAddress = InetSocketAddress(ip, EPAPER_PORT)
            socket.connect(socketAddress, SOCKET_TIMEOUT_MS)
            val connected = socket.isConnected
            socket.close()
            Log.d(TAG, "testEpaperConnection -> connected=$connected (IP: $ip)")
            connected
        } catch (e: Exception) {
            Log.w(TAG, "testEpaperConnection -> failed", e)
            false
        }
    }

    private suspend fun testInternetConnectivity(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Testing internet connectivity...")
            val url = URL("http://baidu.com")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = PING_TIMEOUT_MS
            connection.readTimeout = PING_TIMEOUT_MS
            connection.requestMethod = "HEAD"
            val responseCode = connection.responseCode
            connection.disconnect()

            val success = responseCode in 200..399
            Log.d(TAG, "Internet test: responseCode=$responseCode, success=$success")
            success
        } catch (e: Exception) {
            Log.w(TAG, "HTTP test failed, trying socket: ${e.message}")
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(InetAddress.getByName("baidu.com"), 80), PING_TIMEOUT_MS)
                socket.close()
                Log.d(TAG, "Socket test succeeded")
                true
            } catch (e2: Exception) {
                Log.w(TAG, "Socket test failed: ${e2.message}")
                false
            }
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
                delay(RETRY_DELAY_MS * retries)
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
                delay(RETRY_DELAY_MS * retries)
            }
        }
        Log.w(TAG, "waitForEpaperReady -> failed after $MAX_PING_RETRIES retries")
    }

    private suspend fun pingHost(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
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

    private suspend fun pingEpaperStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            val ip = _discoveredDeviceIp.value ?: EPAPER_IP
            val url = URL("http://$ip/status")
            val connection = url.openConnection() as HttpURLConnection
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

    // ========================
    // LAN Endpoint Detection (from LanReachabilityChecker)
    // ========================

    sealed class EndpointResult {
        data class Success(val baseUrl: String, val resolvedIp: String) : EndpointResult()
        data class Failure(val reason: String) : EndpointResult()
    }

    /**
     * Detect if the device is reachable on LAN with retry logic
     */
    suspend fun detectLanEndpoint(timeoutMs: Long = PROBE_TIMEOUT_MS): EndpointResult =
        withContext(Dispatchers.IO) {
            _isDiscovering.value = true
            var lastFailure: EndpointResult.Failure? = null

            try {
                for (attempt in 0 until DETECT_MAX_ATTEMPTS) {
                    Log.d(TAG, "LAN detection attempt ${attempt + 1}/$DETECT_MAX_ATTEMPTS")

                    val result = performDetection(timeoutMs)
                    if (result is EndpointResult.Success) {
                        return@withContext result
                    }

                    lastFailure = result as? EndpointResult.Failure ?: EndpointResult.Failure("unknown")

                    if (attempt < DETECT_MAX_ATTEMPTS - 1) {
                        Log.w(TAG, "LAN detection failed, retrying in ${DETECT_RETRY_DELAY_MS}ms...")
                        delay(DETECT_RETRY_DELAY_MS)
                    }
                }

                lastFailure ?: EndpointResult.Failure("resolve_failed")
            } finally {
                _isDiscovering.value = false
            }
        }

    private suspend fun performDetection(timeoutMs: Long): EndpointResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting device discovery (timeout: ${timeoutMs}ms)")

        // Method 1: Try NSD discovery
        val nsdIp = discoverViaNsd(timeoutMs)
        if (!nsdIp.isNullOrEmpty()) {
            // Verify the NSD-discovered IP is actually reachable
            if (probeDeviceStatus(nsdIp)) {
                Log.i(TAG, "NSD discovery found reachable device at: $nsdIp")
                return@withContext EndpointResult.Success("http://$nsdIp", nsdIp)
            }
            Log.w(TAG, "NSD discovered $nsdIp but it's not reachable (wrong interface IP?)")
        }

        // Method 2: Fall back to subnet scanning
        Log.d(TAG, "Falling back to subnet scan...")
        val scannedIp = discoverViaSubnetScan()
        if (!scannedIp.isNullOrEmpty()) {
            Log.i(TAG, "Subnet scan found device at: $scannedIp")
            return@withContext EndpointResult.Success("http://$scannedIp", scannedIp)
        }

        Log.w(TAG, "Device discovery failed - no device found")
        return@withContext EndpointResult.Failure("resolve_failed")
    }

    /**
     * Discover the e-paper device by scanning all available subnets
     */
    private suspend fun discoverViaSubnetScan(): String? = withContext(Dispatchers.IO) {
        val subnets = getAllSubnets()
        if (subnets.isEmpty()) {
            Log.w(TAG, "Could not determine any subnets")
            return@withContext null
        }

        Log.d(TAG, "Scanning ${subnets.size} subnet(s): ${subnets.joinToString()}")

        val ownIps = getOwnIpAddresses()
        val result = java.util.concurrent.atomic.AtomicReference<String?>(null)

        coroutineScope {
            // Scan all subnets
            for (subnet in subnets) {
                if (result.get() != null) break

                Log.d(TAG, "Scanning subnet: $subnet.*")

                // Scan IPs 1-254 in parallel chunks
                (1..254).chunked(50).forEach { chunk ->
                    chunk.forEach { i ->
                        val ip = "$subnet.$i"
                        if (ip !in ownIps && result.get() == null) {
                            launch {
                                if (result.get() == null && probeDeviceStatus(ip)) {
                                    if (result.compareAndSet(null, ip)) {
                                        Log.d(TAG, "Subnet scan found device at: $ip")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        result.get()
    }

    /**
     * Get all available subnet prefixes from all network interfaces
     */
    private fun getAllSubnets(): List<String> {
        val subnets = mutableSetOf<String>()

        try {
            // Method 1: Get from all networks via ConnectivityManager
            connectivityManager.allNetworks.forEach { network ->
                val linkProperties = connectivityManager.getLinkProperties(network)
                linkProperties?.linkAddresses?.forEach { linkAddress ->
                    val address = linkAddress.address
                    if (address is java.net.Inet4Address && !address.isLoopbackAddress) {
                        val ip = address.hostAddress ?: return@forEach
                        val parts = ip.split(".")
                        if (parts.size == 4) {
                            subnets.add("${parts[0]}.${parts[1]}.${parts[2]}")
                        }
                    }
                }
            }

            // Method 2: Get from NetworkInterface enumeration (catches hotspot interface)
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    networkInterface.inetAddresses.toList().forEach { address ->
                        if (address is java.net.Inet4Address && !address.isLoopbackAddress) {
                            val ip = address.hostAddress ?: return@forEach
                            val parts = ip.split(".")
                            if (parts.size == 4) {
                                val subnet = "${parts[0]}.${parts[1]}.${parts[2]}"
                                if (subnet !in subnets) {
                                    Log.d(TAG, "Found subnet $subnet from interface ${networkInterface.name}")
                                    subnets.add(subnet)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting subnets", e)
        }

        return subnets.toList()
    }

    /**
     * Get all our own IP addresses
     */
    private fun getOwnIpAddresses(): Set<String> {
        val ips = mutableSetOf<String>()
        try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                networkInterface.inetAddresses.toList().forEach { address ->
                    if (address is java.net.Inet4Address) {
                        address.hostAddress?.let { ips.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return ips
    }

    /**
     * Discover e-paper device using Android NSD (Network Service Discovery).
     * Discovers HTTP services and checks if they respond to /status endpoint.
     */
    private suspend fun discoverViaNsd(timeoutMs: Long): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting NSD discovery for HTTP services")

        val result = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val scope = CoroutineScope(Dispatchers.IO)

        var discoveryListener: NsdManager.DiscoveryListener? = null

        try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    discoveryListener = object : NsdManager.DiscoveryListener {
                        override fun onDiscoveryStarted(serviceType: String) {
                            Log.d(TAG, "NSD discovery started for: $serviceType")
                        }

                        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                            Log.d(TAG, "NSD service found: ${serviceInfo.serviceName}")

                            // Resolve every HTTP service to check if it's the e-paper device
                            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                                    Log.w(TAG, "NSD resolve failed for ${service.serviceName}: $errorCode")
                                }

                                override fun onServiceResolved(service: NsdServiceInfo) {
                                    val host = service.host
                                    val ip = host?.hostAddress
                                    val port = service.port
                                    Log.d(TAG, "NSD resolved: ${service.serviceName} -> $ip:$port")

                                    if (ip != null && ip != "0.0.0.0") {
                                        // Check if this is the e-paper device by hitting /status
                                        scope.launch {
                                            if (probeDeviceStatus(ip) && result.compareAndSet(null, ip)) {
                                                Log.d(TAG, "Found e-paper device at: $ip")
                                                try {
                                                    discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
                                                } catch (e: Exception) {
                                                    // Ignore - might already be stopped
                                                }
                                                if (continuation.isActive) {
                                                    continuation.resume(ip)
                                                }
                                            }
                                        }
                                    }
                                }
                            })
                        }

                        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                            Log.d(TAG, "NSD service lost: ${serviceInfo.serviceName}")
                        }

                        override fun onDiscoveryStopped(serviceType: String) {
                            Log.d(TAG, "NSD discovery stopped for: $serviceType")
                        }

                        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                            Log.e(TAG, "NSD discovery start failed: $errorCode")
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }

                        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                            Log.e(TAG, "NSD discovery stop failed: $errorCode")
                        }
                    }

                    // Start discovery for HTTP services
                    try {
                        nsdManager.discoverServices("_http._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener!!)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start NSD discovery", e)
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                        return@suspendCancellableCoroutine
                    }

                    continuation.invokeOnCancellation {
                        Log.d(TAG, "NSD discovery cancelled/timed out")
                        try {
                            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            } ?: result.get() // Return any partial result on timeout
        } finally {
            scope.cancel()
        }
    }

    /**
     * Check if an IP responds to the e-paper /status endpoint
     */
    private fun probeDeviceStatus(ip: String): Boolean {
        return try {
            val url = URL("http://$ip/status")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            connection.requestMethod = "GET"
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    // ========================
    // HTTP Probing
    // ========================

    private fun probeHttpReachability(ip: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("http://$ip/status")
                .get()
                .build()

            val response = probeClient.newCall(request).execute()
            val success = response.isSuccessful
            response.close()

            Log.d(TAG, "HTTP probe to $ip: ${if (success) "success" else "failed"}")
            success
        } catch (e: Exception) {
            Log.e(TAG, "HTTP probe failed: ${e.message}")
            false
        }
    }

    private fun probeHttpWithCustomDns(resolvedIp: String): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        return if (hostname == DEVICE_LAN_HOST) {
                            Log.d(TAG, "Custom DNS: $hostname -> $resolvedIp")
                            listOf(InetAddress.getByName(resolvedIp))
                        } else {
                            Dns.SYSTEM.lookup(hostname)
                        }
                    }
                })
                .build()

            val request = Request.Builder()
                .url("http://$DEVICE_LAN_HOST/status")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()

            Log.d(TAG, "HTTP probe with custom DNS: ${if (success) "success" else "failed"}")
            success
        } catch (e: Exception) {
            Log.e(TAG, "HTTP probe with custom DNS failed", e)
            false
        }
    }

    // ========================
    // Endpoint Management
    // ========================

    /**
     * Switch to LAN endpoint mode
     */
    fun useLanEndpoint(resolvedIp: String) {
        _connectionMode.value = ConnectionMode.LAN
        _currentBaseUrl.value = "http://$resolvedIp"
        _lastResolvedIp.value = resolvedIp
        _discoveredDeviceIp.value = resolvedIp
        Log.i(TAG, "Switched to LAN mode: baseUrl=${_currentBaseUrl.value}")
    }

    /**
     * Switch to hotspot endpoint mode
     */
    fun useHotspotEndpoint() {
        _connectionMode.value = ConnectionMode.HOTSPOT
        _currentBaseUrl.value = "http://$EPAPER_IP"
        _lastResolvedIp.value = null
        Log.i(TAG, "Switched to hotspot mode: baseUrl=${_currentBaseUrl.value}")
    }

    /**
     * Get the base URL for the e-paper device
     */
    fun getDeviceBaseUrl(): String {
        return _currentBaseUrl.value
    }

    /**
     * Create a custom DNS resolver for LAN mode
     */
    fun createLanDns(): Dns {
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val resolvedIp = _lastResolvedIp.value
                return if (hostname == DEVICE_LAN_HOST && resolvedIp != null) {
                    Log.d(TAG, "LAN DNS: $hostname -> $resolvedIp")
                    listOf(InetAddress.getByName(resolvedIp))
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        }
    }

    // ========================
    // Cleanup
    // ========================

    fun disconnectEpaperHotspot() {
        connectivityManager.bindProcessToNetwork(null)
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        networkCallback = null
        epaperNetwork = null
        _isConnected.value = false
        stopDiscovery()
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

    fun clearCache() {
        cachedInternetNetwork = null
        cachedInternetClient = null
        networkCacheTime = 0
        Log.d(TAG, "Network cache cleared")
    }

    // ========================
    // Network Info
    // ========================

    fun getNetworkInfo(): Map<String, Any?> {
        val network = epaperNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val linkProperties = network?.let { connectivityManager.getLinkProperties(it) }

        return mapOf(
            "connected" to (network != null),
            "ssid" to EPAPER_SSID,
            "targetIp" to (_discoveredDeviceIp.value ?: EPAPER_IP),
            "targetPort" to EPAPER_PORT,
            "connectionMode" to _connectionMode.value.name,
            "baseUrl" to _currentBaseUrl.value,
            "hasWifi" to (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true),
            "hasInternet" to (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true),
            "interfaceName" to linkProperties?.interfaceName
        )
    }

    fun getCurrentNetworkStatus(): String {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return "No network"
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return "Unknown capabilities"

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        "WiFi (Internet)"
                    } else {
                        "WiFi (Local)"
                    }
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Other"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network status", e)
            "Error"
        }
    }
}