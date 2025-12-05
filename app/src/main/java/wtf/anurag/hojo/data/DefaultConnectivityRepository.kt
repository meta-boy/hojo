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
    private val connectivityManager: EpaperConnectivityManager,
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

    // Delegate to connectivity manager's discovery state
    override val isDiscovering: StateFlow<Boolean>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectivityManager.isDiscovering
        } else {
            MutableStateFlow(false)
        }

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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // First try to connect to already-discovered device (fast path)
                    var success = connectivityManager.connectToDiscoveredDevice()

                    if (!success) {
                        // If no discovered device, use the full connection flow
                        success = connectivityManager.connectToDevice()
                    }

                    if (success) {
                        // If we're connected via e-paper's hotspot, bind to it
                        if (connectivityManager.connectionMode.value ==
                            wtf.anurag.hojo.connectivity.EpaperConnectivityManager.ConnectionMode.HOTSPOT) {
                            connectivityManager.bindToEpaperNetwork()
                        }

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
            } finally {
                _isConnecting.value = false
            }
        }
    }

    override suspend fun updateDeviceStatus() {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
             connectivityManager.disconnectEpaperHotspot()
         }
         _isConnected.value = false
    }

    override suspend fun unbindNetwork() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectivityManager.unbindNetwork()
        }
    }

    override suspend fun bindToEpaperNetwork(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectivityManager.bindToEpaperNetwork()
        } else {
            false
        }
    }

    override suspend fun prepareNetworkForApiRequest(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectivityManager.prepareNetworkForApiRequest()
        } else {
            true // Assume internet is available on older devices
        }
    }
}
