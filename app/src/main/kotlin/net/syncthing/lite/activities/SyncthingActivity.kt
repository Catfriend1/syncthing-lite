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
    private var retryDelayMs = 5000L // Start with 5 seconds - faster reconnection
    private val maxRetryDelayMs = 15000L // Maximum 15 seconds - faster than before
    private val connectionRetryIntervalMs = 5000L // Retry connections every 5 seconds - more aggressive
    private var isStarted = false

    companion object {
        private const val TAG = "SyncthingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        isStarted = true

        val binding = DataBindingUtil.inflate<DialogLoadingBinding>(
                LayoutInflater.from(this), R.layout.dialog_loading, null, false)
        binding.loadingText.text = getString(R.string.loading_config_starting_syncthing_client)

        loadingDialog = AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(binding.root)
                .show()

        libraryHandler.start {
            if (!isDestroyed) {
                loadingDialog?.dismiss()
            }
            onLibraryLoaded()
        }
    }

    override fun onStop() {
        super.onStop()
        isStarted = false

        connectionManagerJob?.cancel()
        connectionManagerJob = null
        
        connectionRetryJob?.cancel()
        connectionRetryJob = null
        
        libraryHandler.stop()
        loadingDialog?.dismiss()
    }

    open fun onLibraryLoaded() {
        // For MainActivity, enable discovery when library is loaded
        if (this is MainActivity) {
            libraryHandler.enableLocalDiscovery()
            libraryHandler.enableGlobalDiscovery()
        }
        
        startConnectionManager()
        startConnectionRetryJob()
        
        // For MainActivity, ensure immediate connection attempt
        if (this is MainActivity) {
            triggerImmediateConnectionAttempt()
        }
    }

    /**
     * Centralized connection manager that handles discovery and connection establishment
     */
    private fun startConnectionManager() {
        connectionManagerJob?.cancel()
        connectionManagerJob = launch {
            // Immediate connection attempt on startup
            tryConnectToAllDevices()
            
            // Monitor connection status continuously
            libraryHandler.subscribeToConnectionStatus().collect { connectionInfo ->
                if (isDestroyed || !isStarted) return@collect
                
                val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }
                
                // Check for devices that need discovery or connection
                val devicesNeedingDiscovery = devices.filter { device ->
                    val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                    connection.status == ConnectionStatus.Disconnected && connection.addresses.isEmpty()
                }
                
                val devicesNeedingConnection = devices.filter { device ->
                    val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                    connection.status == ConnectionStatus.Disconnected && connection.addresses.isNotEmpty()
                }
                
                // Handle devices without addresses - need discovery
                if (devicesNeedingDiscovery.isNotEmpty()) {
                    retryDiscoveryWithBackoff()
                }
                
                // Handle devices with addresses but not connected
                if (devicesNeedingConnection.isNotEmpty()) {
                    tryConnectToAllDevices()
                }
            }
        }
    }

    /**
     * Periodic connection retry job that attempts to reconnect to disconnected devices
     */
    private fun startConnectionRetryJob() {
        connectionRetryJob?.cancel()
        connectionRetryJob = launch {
            while (isStarted && !isDestroyed) {
                try {
                    delay(connectionRetryIntervalMs)
                    
                    if (!isStarted || isDestroyed) break
                    
                    // Get current connection status
                    val connectionInfo = libraryHandler.subscribeToConnectionStatus().value
                    val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }
                    
                    // Find devices that have addresses but are disconnected
                    val devicesNeedingReconnection = devices.filter { device ->
                        val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                        connection.status == ConnectionStatus.Disconnected && connection.addresses.isNotEmpty()
                    }
                    
                    if (devicesNeedingReconnection.isNotEmpty()) {
                        launch(Dispatchers.IO) {
                            try {
                                libraryHandler.libraryManager.withLibrary { library ->
                                    library.syncthingClient.connectToNewlyAddedDevices()
                                }
                                
                                devicesNeedingReconnection.forEach { device ->
                                    libraryHandler.libraryManager.withLibrary { library ->
                                        library.syncthingClient.reconnect(device.deviceId)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Connection retry error", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Log.e(TAG, "Connection retry job error", e)
                    }
                }
            }
        }
    }

    /**
     * Attempts to connect to all devices and trigger discovery
     */
    private suspend fun tryConnectToAllDevices() {
        launch(Dispatchers.IO) {
            try {
                libraryHandler.libraryManager.withLibrary { library ->
                    library.syncthingClient.connectToNewlyAddedDevices()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in connectToNewlyAddedDevices()", e)
            }
        }
        
        // Trigger discovery for devices without addresses
        libraryHandler.retryDiscoveryForDevicesWithoutAddresses()
        
        // For MainActivity: force reconnection to all known devices
        if (this is MainActivity) {
            launch(Dispatchers.IO) {
                try {
                    libraryHandler.libraryManager.withLibrary { library ->
                        val devices = library.configuration.peers
                        devices.forEach { device ->
                            library.syncthingClient.reconnect(device.deviceId)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reconnecting to devices", e)
                }
            }
        }
    }

    /**
     * Retry discovery with exponential backoff strategy
     */
    private suspend fun retryDiscoveryWithBackoff() {
        delay(retryDelayMs)
        
        libraryHandler.retryDiscoveryForDevicesWithoutAddresses()
        
        retryDelayMs = min(retryDelayMs * 2, maxRetryDelayMs)
    }

    /**
     * Reset the retry delay when a successful connection is established
     */
    fun resetRetryDelay() {
        retryDelayMs = 5000L
    }

    /**
     * Trigger immediate discovery and connection (called when new devices are added)
     */
    fun triggerImmediateConnectionAttempt() {
        launch {
            resetRetryDelay()
            tryConnectToAllDevices()
        }
    }
}
