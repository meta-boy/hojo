package wtf.anurag.hojo.ui.viewmodels

import android.app.Application
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

    val BASE_URL = "http://192.168.3.3"

    init {
        checkConnection()
        // Poll for connection if not connected? Or just rely on callbacks?
        // RN uses setInterval.
        viewModelScope.launch {
            while (true) {
                if (!_isConnected.value && !_isConnecting.value) {
                    handleConnect(silent = true)
                }
                delay(5000)
            }
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            // In a real app, we'd check actual wifi status
            // For now, let's assume if we can hit the API, we are connected
            // Or use the manager's isConnected logic if implemented
            // _isConnected.value = connectivityManager.isConnected() // If we had this
        }
    }

    fun handleConnect(silent: Boolean = false) {
        if (_isConnecting.value) return
        _isConnecting.value = true
        viewModelScope.launch {
            try {
                val success = connectivityManager.connectToEpaperHotspot()
                if (success) {
                    _isConnected.value = true
                    connectivityManager.bindToEpaperNetwork()
                    updateDeviceStatus()
                }
            } catch (e: Exception) {
                if (!silent) {
                    // Show error
                }
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
                val status = repository.fetchStatus(BASE_URL)
                _storageStatus.value = status
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
