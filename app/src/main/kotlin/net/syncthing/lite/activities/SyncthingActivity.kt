package net.syncthing.lite.activities

import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import android.view.LayoutInflater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.syncthing.java.bep.connectionactor.ConnectionInfo
import net.syncthing.java.bep.connectionactor.ConnectionStatus
import net.syncthing.lite.R
import net.syncthing.lite.async.CoroutineActivity
import net.syncthing.lite.databinding.DialogLoadingBinding
import net.syncthing.lite.library.LibraryHandler
import kotlin.math.min

abstract class SyncthingActivity : CoroutineActivity() {
    val libraryHandler: LibraryHandler by lazy {
        LibraryHandler(
                context = this@SyncthingActivity
        )
    }
    private var loadingDialog: AlertDialog? = null
    private var snackBar: Snackbar? = null
    private var connectionManagerJob: Job? = null
    private var connectionRetryJob: Job? = null
    private var retryDelayMs = 15000L // Start with 15 seconds as requested
    private val maxRetryDelayMs = 60000L // Maximum 1 minute as requested
    private val connectionRetryIntervalMs = 15000L // Retry connections every 15 seconds
    private var isStarted = false

    companion object {
        private const val TAG = "SyncthingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() called for ${this.javaClass.simpleName}")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart() called for ${this.javaClass.simpleName}")
        isStarted = true

        val binding = DataBindingUtil.inflate<DialogLoadingBinding>(
                LayoutInflater.from(this), R.layout.dialog_loading, null, false)
        binding.loadingText.text = getString(R.string.loading_config_starting_syncthing_client)

        loadingDialog = AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(binding.root)
                .show()

        Log.d(TAG, "Starting LibraryHandler for ${this.javaClass.simpleName}")
        libraryHandler.start {
            Log.d(TAG, "LibraryHandler started for ${this.javaClass.simpleName}")
            if (!isDestroyed) {
                loadingDialog?.dismiss()
            }

            onLibraryLoaded()
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop() called for ${this.javaClass.simpleName}")
        isStarted = false

        // Stop the connection manager
        Log.d(TAG, "Stopping connection manager for ${this.javaClass.simpleName}")
        connectionManagerJob?.cancel()
        connectionManagerJob = null
        
        // Stop the connection retry job
        Log.d(TAG, "Stopping connection retry job for ${this.javaClass.simpleName}")
        connectionRetryJob?.cancel()
        connectionRetryJob = null
        
        Log.d(TAG, "Stopping LibraryHandler for ${this.javaClass.simpleName}")
        libraryHandler.stop()
        loadingDialog?.dismiss()
    }

    open fun onLibraryLoaded() {
        Log.d(TAG, "onLibraryLoaded() called for ${this.javaClass.simpleName}")
        
        // For MainActivity, always enable discovery when library is loaded
        // This ensures discovery works properly after IntroActivity transition
        if (this is MainActivity) {
            Log.d(TAG, "MainActivity detected - enabling discovery")
            libraryHandler.enableDiscovery()
        }
        
        // Start the centralized connection manager
        startConnectionManager()
        
        // Start the connection retry job
        startConnectionRetryJob()
        
        // For MainActivity, ensure we trigger immediate connection attempts
        // This is especially important after IntroActivity transition
        if (this is MainActivity) {
            Log.d(TAG, "MainActivity detected - triggering immediate connection attempt")
            triggerImmediateConnectionAttempt()
        }
    }

    /**
     * Centralized connection manager that handles discovery and connection establishment
     * with proper backoff strategy and lifecycle management.
     */
    private fun startConnectionManager() {
        Log.d(TAG, "Starting connection manager for ${this.javaClass.simpleName}")
        connectionManagerJob?.cancel()
        connectionManagerJob = launch {
            Log.d(TAG, "Connection manager coroutine started for ${this.javaClass.simpleName}")
            
            // Immediate connection attempt on startup
            Log.d(TAG, "Triggering immediate connection attempt for ${this.javaClass.simpleName}")
            tryConnectToAllDevices()
            
            // Monitor connection status continuously
            Log.d(TAG, "Starting connection status monitoring for ${this.javaClass.simpleName}")
            libraryHandler.subscribeToConnectionStatus().collect { connectionInfo ->
                if (isDestroyed || !isStarted) {
                    Log.d(TAG, "Connection manager stopping due to destroyed/stopped state for ${this.javaClass.simpleName}")
                    return@collect
                }
                
                Log.d(TAG, "Connection status update received for ${this.javaClass.simpleName}: ${connectionInfo.size} devices")
                
                val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }
                Log.d(TAG, "Found ${devices.size} configured devices for ${this.javaClass.simpleName}")
                
                // Check for devices that need discovery or connection
                val devicesNeedingDiscovery = devices.filter { device ->
                    val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                    connection.status == ConnectionStatus.Disconnected && connection.addresses.isEmpty()
                }
                
                val devicesNeedingConnection = devices.filter { device ->
                    val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                    connection.status == ConnectionStatus.Disconnected && connection.addresses.isNotEmpty()
                }
                
                // Also check for devices that had socket connection closed (normal after certificate exchange)
                val devicesWithSocketClosed = devices.filter { device ->
                    val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                    connection.status == ConnectionStatus.Disconnected && connection.addresses.isNotEmpty()
                }
                
                Log.d(TAG, "Devices needing discovery: ${devicesNeedingDiscovery.size}, needing connection: ${devicesNeedingConnection.size}, with socket closed: ${devicesWithSocketClosed.size} for ${this.javaClass.simpleName}")
                
                // Handle devices without addresses - need discovery
                if (devicesNeedingDiscovery.isNotEmpty()) {
                    Log.d(TAG, "Triggering discovery retry for ${devicesNeedingDiscovery.size} devices in ${this.javaClass.simpleName}")
                    retryDiscoveryWithBackoff()
                }
                
                // Handle devices with addresses but not connected - need connection
                // This includes devices that had socket closed after certificate exchange
                if (devicesNeedingConnection.isNotEmpty()) {
                    Log.d(TAG, "Triggering connection attempt for ${devicesNeedingConnection.size} devices in ${this.javaClass.simpleName}")
                    tryConnectToAllDevices()
                }
                
                // Log successful connections for debugging
                val connectedDevices = connectionInfo.values.filter { it.status == ConnectionStatus.Connected }
                if (connectedDevices.isNotEmpty()) {
                    Log.d(TAG, "Successfully connected devices: ${connectedDevices.size} for ${this.javaClass.simpleName}")
                }
            }
        }
    }

    /**
     * Periodic connection retry job that continuously attempts to reconnect
     * to devices that have addresses but are disconnected.
     * This is crucial for handling the "socket close after certificate exchange" scenario.
     */
    private fun startConnectionRetryJob() {
        Log.d(TAG, "Starting connection retry job for ${this.javaClass.simpleName}")
        connectionRetryJob?.cancel()
        connectionRetryJob = launch {
            Log.d(TAG, "Connection retry job coroutine started for ${this.javaClass.simpleName}")
            
            while (isStarted && !isDestroyed) {
                try {
                    // Wait before checking
                    delay(connectionRetryIntervalMs)
                    
                    if (!isStarted || isDestroyed) {
                        Log.d(TAG, "Connection retry job stopping due to destroyed/stopped state for ${this.javaClass.simpleName}")
                        break
                    }
                    
                    Log.d(TAG, "Connection retry job checking for disconnected devices with addresses for ${this.javaClass.simpleName}")
                    
                    // Get current connection status
                    val connectionInfo = libraryHandler.subscribeToConnectionStatus().value
                    val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }
                    
                    // Find devices that have addresses but are disconnected
                    val devicesNeedingReconnection = devices.filter { device ->
                        val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                        connection.status == ConnectionStatus.Disconnected && connection.addresses.isNotEmpty()
                    }
                    
                    if (devicesNeedingReconnection.isNotEmpty()) {
                        Log.d(TAG, "Connection retry job found ${devicesNeedingReconnection.size} devices needing reconnection for ${this.javaClass.simpleName}")
                        
                        // Attempt to reconnect to these devices
                        launch(Dispatchers.IO) {
                            try {
                                Log.d(TAG, "Connection retry job calling connectToNewlyAddedDevices() for ${this.javaClass.simpleName}")
                                libraryHandler.libraryManager.withLibrary { library ->
                                    library.syncthingClient.connectToNewlyAddedDevices()
                                }
                                
                                // Also try individual reconnection for each device
                                devicesNeedingReconnection.forEach { device ->
                                    Log.d(TAG, "Connection retry job attempting reconnect to device: ${device.deviceId.deviceId.substring(0, 8)} for ${this.javaClass.simpleName}")
                                    libraryHandler.libraryManager.withLibrary { library ->
                                        library.syncthingClient.reconnect(device.deviceId)
                                    }
                                }
                                
                                Log.d(TAG, "Connection retry job completed reconnection attempts for ${this.javaClass.simpleName}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Connection retry job error in reconnection for ${this.javaClass.simpleName}", e)
                            }
                        }
                    } else {
                        Log.d(TAG, "Connection retry job found no devices needing reconnection for ${this.javaClass.simpleName}")
                    }
                } catch (e: Exception) {
                    // CancellationException is normal when coroutine is cancelled
                    if (e is CancellationException) {
                        Log.d(TAG, "Connection retry job cancelled for ${this.javaClass.simpleName}")
                    } else {
                        Log.e(TAG, "Connection retry job error for ${this.javaClass.simpleName}", e)
                    }
                }
            }
            
            Log.d(TAG, "Connection retry job coroutine ended for ${this.javaClass.simpleName}")
        }
    }

    /**
     * Immediately attempts to connect to all devices and trigger discovery
     */
    private suspend fun tryConnectToAllDevices() {
        Log.d(TAG, "tryConnectToAllDevices() called for ${this.javaClass.simpleName}")
        
        // First try to connect to devices that already have addresses
        launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Calling connectToNewlyAddedDevices() for ${this.javaClass.simpleName}")
                libraryHandler.libraryManager.withLibrary { library ->
                    library.syncthingClient.connectToNewlyAddedDevices()
                }
                Log.d(TAG, "connectToNewlyAddedDevices() completed for ${this.javaClass.simpleName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error in connectToNewlyAddedDevices() for ${this.javaClass.simpleName}", e)
            }
        }
        
        // Also trigger discovery for devices without addresses
        Log.d(TAG, "Triggering discovery retry for ${this.javaClass.simpleName}")
        libraryHandler.retryDiscoveryForDevicesWithoutAddresses()
        
        // Additional step for MainActivity: force reconnection to all known devices
        // This helps with the IntroActivity -> MainActivity transition
        if (this is MainActivity) {
            Log.d(TAG, "MainActivity - forcing reconnection to all known devices")
            launch(Dispatchers.IO) {
                try {
                    libraryHandler.libraryManager.withLibrary { library ->
                        val devices = library.configuration.peers
                        Log.d(TAG, "MainActivity - attempting to reconnect to ${devices.size} devices")
                        devices.forEach { device ->
                            Log.d(TAG, "MainActivity - reconnecting to device: ${device.deviceId}")
                            library.syncthingClient.reconnect(device.deviceId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "MainActivity - Error reconnecting to devices", e)
                }
            }
        }
    }

    /**
     * Retry discovery with exponential backoff strategy
     */
    private suspend fun retryDiscoveryWithBackoff() {
        Log.d(TAG, "retryDiscoveryWithBackoff() called with delay ${retryDelayMs}ms for ${this.javaClass.simpleName}")
        
        // Apply exponential backoff
        delay(retryDelayMs)
        
        // Trigger discovery
        Log.d(TAG, "Triggering discovery retry after backoff for ${this.javaClass.simpleName}")
        libraryHandler.retryDiscoveryForDevicesWithoutAddresses()
        
        // Increase delay for next retry (exponential backoff)
        val oldDelay = retryDelayMs
        retryDelayMs = min(retryDelayMs * 2, maxRetryDelayMs)
        Log.d(TAG, "Backoff delay increased from ${oldDelay}ms to ${retryDelayMs}ms for ${this.javaClass.simpleName}")
    }

    /**
     * Reset the retry delay when a successful connection is established
     */
    fun resetRetryDelay() {
        Log.d(TAG, "resetRetryDelay() called for ${this.javaClass.simpleName}")
        retryDelayMs = 15000L // Reset to 15 seconds as requested
    }

    /**
     * Trigger immediate discovery and connection (called when new devices are added)
     */
    fun triggerImmediateConnectionAttempt() {
        Log.d(TAG, "triggerImmediateConnectionAttempt() called for ${this.javaClass.simpleName}")
        launch {
            resetRetryDelay()
            tryConnectToAllDevices()
        }
    }
}
