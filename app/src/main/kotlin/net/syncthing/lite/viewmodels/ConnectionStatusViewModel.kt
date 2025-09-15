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
 * ViewModel zur zentralen Verwaltung des Verbindungsstatus von Syncthing-Geräten.
 * Stellt die Anzahl der aktuell verbundenen Geräte als StateFlow bereit.
 */
class ConnectionStatusViewModel : ViewModel() {
    
    private val _connectedDeviceCount = MutableStateFlow(0)
    val connectedDeviceCount: StateFlow<Int> = _connectedDeviceCount.asStateFlow()
    
    private var libraryHandler: LibraryHandler? = null
    
    /**
     * Initialisiert das ViewModel mit dem LibraryHandler und startet die Überwachung
     * des Verbindungsstatus.
     */
    fun initialize(libraryHandler: LibraryHandler) {
        this.libraryHandler = libraryHandler
        observeConnectionStatus()
    }
    
    /**
     * Überwacht den Verbindungsstatus aller Geräte und aktualisiert die Anzahl
     * der verbundenen Geräte automatisch.
     */
    private fun observeConnectionStatus() {
        viewModelScope.launch {
            libraryHandler?.subscribeToConnectionStatus()?.collectLatest { connectionInfoMap ->
                // Zähle alle Geräte, die aktuell verbunden sind
                val connectedCount = connectionInfoMap.values.count { connectionInfo ->
                    connectionInfo.status == ConnectionStatus.Connected
                }
                _connectedDeviceCount.value = connectedCount
            }
        }
    }
}