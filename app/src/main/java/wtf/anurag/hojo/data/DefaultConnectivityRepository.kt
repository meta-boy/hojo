package wtf.anurag.hojo.data

import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import wtf.anurag.hojo.connectivity.EpaperConnectivityManager
import wtf.anurag.hojo.data.model.StorageStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultConnectivityRepository @Inject constructor(
    private val connectivityManager: EpaperConnectivityManager?,
    private val fileManagerRepository: FileManagerRepository
) : ConnectivityRepository {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    override val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _storageStatus = MutableStateFlow<StorageStatus?>(null)
    override val storageStatus: StateFlow<StorageStatus?> = _storageStatus.asStateFlow()

    private val _deviceBaseUrl = MutableStateFlow("http://192.168.3.3")
    override val deviceBaseUrl: StateFlow<String> = _deviceBaseUrl.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    override suspend fun checkConnection() {
        // Check if we can hit the API with current base URL
        try {
            val status = fileManagerRepository.fetchStatus(_deviceBaseUrl.value)
            _storageStatus.value = status
            _isConnected.value = true
        } catch (e: Exception) {
            _isConnected.value = false
        }
    }

    override suspend fun handleConnect(silent: Boolean) {
        if (_isConnecting.value) return
        _isConnecting.value = true
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && connectivityManager != null) {
                    // First, try to connect to E-Paper hotspot
                    val success = connectivityManager.connectToEpaperHotspot()
                    if (success) {
                        connectivityManager.bindToEpaperNetwork()

                        // Try to discover device on the network
                        _isDiscovering.value = true
                        val discoveredIp = connectivityManager.discoverDeviceOnNetwork()
                        if (discoveredIp != null) {
                            Log.d("ConnectivityRepository", "Discovered device at $discoveredIp")
                        }
                        _isDiscovering.value = false

                        // Update base URL with discovered IP or use default
                        _deviceBaseUrl.value = connectivityManager.getDeviceBaseUrl()

                        // Verify connection and update status
                        _isConnected.value = true
                        updateDeviceStatus()
                    }
                }
            } catch (e: Exception) {
                if (!silent) {
                    // Log error
                    e.printStackTrace()
                }
                _isDiscovering.value = false
            } finally {
                _isConnecting.value = false
            }
        }
    }

    override suspend fun updateDeviceStatus() {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && connectivityManager != null) {
                    if (!connectivityManager.bindToEpaperNetwork()) {
                        // Try to bind again?
                    }
                }
                val status = fileManagerRepository.fetchStatus(_deviceBaseUrl.value)
                _storageStatus.value = status
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override suspend fun disconnect() {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && connectivityManager != null) {
             connectivityManager.disconnectEpaperHotspot()
         }
         _isConnected.value = false
    }

    override suspend fun unbindNetwork() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && connectivityManager != null) {
            connectivityManager.unbindNetwork()
        }
    }

    override suspend fun bindToEpaperNetwork(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && connectivityManager != null) {
            connectivityManager.bindToEpaperNetwork()
        } else {
            false
        }
    }
}
