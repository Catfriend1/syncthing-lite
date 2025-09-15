package net.syncthing.lite.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.syncthing.java.bep.connectionactor.ConnectionInfo
import net.syncthing.java.bep.connectionactor.ConnectionStatus
import net.syncthing.lite.library.LibraryHandler

/**
 * ViewModel for centralized management of Syncthing device connection status.
 * Provides the number of currently connected devices as StateFlow.
 */
class ConnectionStatusViewModel : ViewModel() {
    
    private val _connectedDeviceCount = MutableStateFlow(0)
    val connectedDeviceCount: StateFlow<Int> = _connectedDeviceCount.asStateFlow()
    
    private var libraryHandler: LibraryHandler? = null
    
    /**
     * Initializes the ViewModel with the LibraryHandler and starts monitoring
     * the connection status.
     */
    fun initialize(libraryHandler: LibraryHandler) {
        this.libraryHandler = libraryHandler
        observeConnectionStatus()
    }
    
    /**
     * Monitors the connection status of all devices and automatically updates the count
     * of connected devices.
     */
    private fun observeConnectionStatus() {
        viewModelScope.launch {
            libraryHandler?.subscribeToConnectionStatus()?.collectLatest { connectionInfoMap ->
                // Count all devices that are currently connected
                val connectedCount = connectionInfoMap.values.count { connectionInfo ->
                    connectionInfo.status == ConnectionStatus.Connected
                }
                _connectedDeviceCount.value = connectedCount
            }
        }
    }
}