package net.syncthing.lite.library

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.syncthing.java.bep.connectionactor.ConnectionInfo
import net.syncthing.java.bep.connectionactor.ConnectionStatus
import net.syncthing.java.core.beans.DeviceInfo
import kotlin.math.min

/**
 * Manages connections and discovery for Syncthing devices.
 * This class handles the common connection logic shared between IntroActivity and SyncthingActivity.
 */
class ConnectionManager(
    private val libraryHandler: LibraryHandler,
    private val scope: CoroutineScope,
    private val tag: String
) {
    private var connectionManagerJob: Job? = null
    private var connectionRetryJob: Job? = null
    private var retryDelayMs = 15000L // Start with 15 seconds
    private val maxRetryDelayMs = 30000L // Maximum 30 seconds
    private val connectionRetryIntervalMs = 15000L // Retry connections every 15 seconds
    private var isStarted = false
    private var shouldRunDiscovery: () -> Boolean = { true }

    companion object {
        private const val TAG_BASE = "ConnectionManager"
    }

    /**
     * Start the connection manager with discovery and connection logic
     */
    fun start(discoveryCondition: () -> Boolean = { true }) {
        if (isStarted) return
        
        isStarted = true
        shouldRunDiscovery = discoveryCondition
        
        Log.d(tag, "Starting ConnectionManager")
        startConnectionManager()
        startConnectionRetryJob()
    }

    /**
     * Stop the connection manager
     */
    fun stop() {
        if (!isStarted) return
        
        isStarted = false
        Log.d(tag, "Stopping ConnectionManager")
        
        connectionManagerJob?.cancel()
        connectionManagerJob = null
        
        connectionRetryJob?.cancel()
        connectionRetryJob = null
    }

    /**
     * Centralized connection manager that handles discovery and connection establishment
     * with proper backoff strategy and lifecycle management.
     */
    private fun startConnectionManager() {
        connectionManagerJob?.cancel()
        connectionManagerJob = scope.launch {
            // Monitor connection status continuously
            libraryHandler.subscribeToConnectionStatus().collect { connectionInfo ->
                if (!isStarted) {
                    return@collect
                }
                
                val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }
                
                // Check for devices that need discovery or connection
                val devicesNeedingDiscovery = devices.filter { device ->
                    val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                    connection.status == ConnectionStatus.Disconnected && connection.addresses.isEmpty()
                }
                
                val devicesNeedingConnection = devices.filter { device ->
                    val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                    connection.addresses.isNotEmpty() && connection.status != ConnectionStatus.Connected
                }
                
                // Only trigger discovery if conditions are met
                if (devicesNeedingDiscovery.isNotEmpty() && shouldRunDiscovery()) {
                    Log.d(tag, "ConnectionManager triggering discovery retry for ${devicesNeedingDiscovery.size} devices")
                    retryDiscoveryWithBackoff()
                }
                
                // Handle devices with addresses but not connected - need connection retry
                if (devicesNeedingConnection.isNotEmpty()) {
                    Log.d(tag, "ConnectionManager triggering connection attempt for ${devicesNeedingConnection.size} devices")
                    tryConnectToDevicesWithAddresses(devicesNeedingConnection)
                }
            }
        }
    }

    /**
     * Periodic connection retry job that continuously attempts to reconnect
     * to devices that have addresses but are disconnected.
     */
    private fun startConnectionRetryJob() {
        connectionRetryJob?.cancel()
        connectionRetryJob = scope.launch {
            while (isStarted) {
                try {
                    // Wait before checking
                    delay(connectionRetryIntervalMs)
                    
                    if (!isStarted) {
                        break
                    }
                    
                    // Only run connection retry if discovery condition is met
                    if (!shouldRunDiscovery()) {
                        continue
                    }
                    
                    // Get current connection status
                    val connectionInfo = libraryHandler.subscribeToConnectionStatus().value
                    val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }
                    
                    // Find devices that have addresses but are disconnected or need reconnection
                    val devicesNeedingReconnection = devices.filter { device ->
                        val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                        connection.addresses.isNotEmpty() && connection.status != ConnectionStatus.Connected
                    }
                    
                    if (devicesNeedingReconnection.isNotEmpty()) {
                        Log.d(tag, "ConnectionManager connection retry job found ${devicesNeedingReconnection.size} devices needing reconnection")
                        
                        // Attempt to reconnect to these devices
                        scope.launch(Dispatchers.IO) {
                            try {
                                libraryHandler.libraryManager.withLibrary { library ->
                                    library.syncthingClient.connectToNewlyAddedDevices()
                                }
                                
                                // Also try individual reconnection for each device
                                devicesNeedingReconnection.forEach { device ->
                                    libraryHandler.libraryManager.withLibrary { library ->
                                        library.syncthingClient.reconnect(device.deviceId)
                                    }
                                }
                                
                                Log.d(tag, "ConnectionManager connection retry job completed reconnection attempts")
                            } catch (e: Exception) {
                                Log.e(tag, "ConnectionManager connection retry job error in reconnection", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // CancellationException is normal when coroutine is cancelled
                    if (e is CancellationException) {
                        Log.d(tag, "ConnectionManager connection retry job cancelled")
                    } else {
                        Log.e(tag, "ConnectionManager connection retry job error", e)
                    }
                }
            }
            
            Log.d(tag, "ConnectionManager connection retry job coroutine ended")
        }
    }

    /**
     * Immediately attempts to connect to all devices and trigger discovery
     */
    suspend fun tryConnectToAllDevices() {
        // First trigger global and local discovery with more aggressive retry
        scope.launch(Dispatchers.IO) {
            try {
                // Multiple discovery attempts to ensure both local and global discovery run
                for (i in 1..3) {
                    libraryHandler.retryDiscoveryForDevicesWithoutAddresses()
                    delay(2000) // 2 second delay between attempts
                }
            } catch (e: Exception) {
                Log.e(tag, "ConnectionManager error in discovery attempts", e)
            }
        }
        
        // Then try to connect to devices that already have addresses
        scope.launch(Dispatchers.IO) {
            try {
                libraryHandler.libraryManager.withLibrary { library ->
                    library.syncthingClient.connectToNewlyAddedDevices()
                }
            } catch (e: Exception) {
                Log.e(tag, "ConnectionManager error in connectToNewlyAddedDevices()", e)
            }
        }
    }

    /**
     * Attempts to connect to devices that already have addresses (without triggering discovery)
     */
    private suspend fun tryConnectToDevicesWithAddresses(devicesNeedingConnection: List<DeviceInfo>) {
        scope.launch(Dispatchers.IO) {
            try {
                libraryHandler.libraryManager.withLibrary { library ->
                    library.syncthingClient.connectToNewlyAddedDevices()
                }
                
                // Also try individual reconnection for each device
                devicesNeedingConnection.forEach { device ->
                    libraryHandler.libraryManager.withLibrary { library ->
                        library.syncthingClient.reconnect(device.deviceId)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "ConnectionManager error in tryConnectToDevicesWithAddresses()", e)
            }
        }
    }

    /**
     * Retry discovery with exponential backoff strategy
     */
    private suspend fun retryDiscoveryWithBackoff() {
        // Apply exponential backoff
        delay(retryDelayMs)
        
        // Trigger discovery multiple times to ensure global discovery runs
        scope.launch(Dispatchers.IO) {
            try {
                // Try discovery multiple times to ensure global discovery server is contacted
                for (i in 1..2) {
                    libraryHandler.retryDiscoveryForDevicesWithoutAddresses()
                    delay(1000) // 1 second between attempts
                }
            } catch (e: Exception) {
                Log.e(tag, "ConnectionManager error in retryDiscoveryWithBackoff()", e)
            }
        }
        
        // Increase delay for next retry (exponential backoff)
        val oldDelay = retryDelayMs
        retryDelayMs = min(retryDelayMs * 2, maxRetryDelayMs)
        Log.d(tag, "ConnectionManager backoff delay increased from ${oldDelay}ms to ${retryDelayMs}ms")
    }

    /**
     * Reset the retry delay when a successful connection is established
     */
    fun resetRetryDelay() {
        retryDelayMs = 15000L // Reset to 15 seconds
    }

    /**
     * Trigger immediate discovery and connection (called when new devices are added)
     */
    fun triggerImmediateConnectionAttempt() {
        scope.launch {
            resetRetryDelay()
            tryConnectToAllDevices()
        }
    }
}