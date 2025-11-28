package wtf.anurag.hojo.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wtf.anurag.hojo.connectivity.EpaperConnectivityManager
import wtf.anurag.hojo.data.FileManagerRepository
import wtf.anurag.hojo.data.model.StorageStatus

class ConnectivityViewModel(application: Application) : AndroidViewModel(application) {
    private val connectivityManager = EpaperConnectivityManager(application)
    private val repository = FileManagerRepository()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _storageStatus = MutableStateFlow<StorageStatus?>(null)
    val storageStatus: StateFlow<StorageStatus?> = _storageStatus.asStateFlow()

    private val _deviceBaseUrl = MutableStateFlow("http://192.168.3.3")
    val deviceBaseUrl: StateFlow<String> = _deviceBaseUrl.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    init {
        checkConnection()
        // Poll for connection if not connected, and refresh storage status when connected
        viewModelScope.launch {
            while (true) {
                if (!_isConnected.value && !_isConnecting.value) {
                    handleConnect(silent = true)
                } else if (_isConnected.value) {
                    // Refresh storage status when connected
                    updateDeviceStatus()
                }
                delay(5000)
            }
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            // Check if we can hit the API with current base URL
            try {
                val status = repository.fetchStatus(_deviceBaseUrl.value)
                _storageStatus.value = status
                _isConnected.value = true
            } catch (e: Exception) {
                _isConnected.value = false
            }
        }
    }

    fun handleConnect(silent: Boolean = false) {
        if (_isConnecting.value) return
        _isConnecting.value = true
        viewModelScope.launch {
            try {
                // First, try to connect to E-Paper hotspot
                val success = connectivityManager.connectToEpaperHotspot()
                if (success) {
                    connectivityManager.bindToEpaperNetwork()

                    // Try to discover device on the network
                    _isDiscovering.value = true
                    val discoveredIp = connectivityManager.discoverDeviceOnNetwork()
                    if (discoveredIp != null) {
                        Log.d("ConnectivityViewModel", "Discovered device at $discoveredIp")
                    }
                    _isDiscovering.value = false

                    // Update base URL with discovered IP or use default
                    _deviceBaseUrl.value = connectivityManager.getDeviceBaseUrl()

                    // Verify connection and update status
                    _isConnected.value = true
                    updateDeviceStatus()
                }
            } catch (e: Exception) {
                if (!silent) {
                    // Show error
                }
                _isDiscovering.value = false
            } finally {
                _isConnecting.value = false
            }
        }
    }

    fun updateDeviceStatus() {
        viewModelScope.launch {
            try {
                if (!connectivityManager.bindToEpaperNetwork()) {
                    // Try to bind again?
                }
                val status = repository.fetchStatus(_deviceBaseUrl.value)
                _storageStatus.value = status
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
