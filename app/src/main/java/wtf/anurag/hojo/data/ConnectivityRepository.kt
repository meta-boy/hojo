package wtf.anurag.hojo.data

import kotlinx.coroutines.flow.StateFlow
import wtf.anurag.hojo.data.model.StorageStatus

interface ConnectivityRepository {
    val isConnected: StateFlow<Boolean>
    val isConnecting: StateFlow<Boolean>
    val deviceBaseUrl: StateFlow<String>
    val storageStatus: StateFlow<StorageStatus?>
    val isDiscovering: StateFlow<Boolean>

    suspend fun checkConnection()
    suspend fun handleConnect(silent: Boolean = false)
    suspend fun updateDeviceStatus()
    suspend fun disconnect()

    // For QuickLinkViewModel - network switching
    suspend fun unbindNetwork()
    suspend fun bindToEpaperNetwork(): Boolean
    suspend fun prepareNetworkForApiRequest(): Boolean  // Ensures internet access
}
