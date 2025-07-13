package net.syncthing.lite.activities

import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import android.view.LayoutInflater
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
    private var retryDelayMs = 10000L // Start with 10 seconds
    private val maxRetryDelayMs = 300000L // Maximum 5 minutes
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
        
        Log.d(TAG, "Stopping LibraryHandler for ${this.javaClass.simpleName}")
        libraryHandler.stop()
        loadingDialog?.dismiss()
    }

    open fun onLibraryLoaded() {
        Log.d(TAG, "onLibraryLoaded() called for ${this.javaClass.simpleName}")
        // Start the centralized connection manager
        startConnectionManager()
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
                
                Log.d(TAG, "Devices needing discovery: ${devicesNeedingDiscovery.size}, needing connection: ${devicesNeedingConnection.size} for ${this.javaClass.simpleName}")
                
                // Handle devices without addresses - need discovery
                if (devicesNeedingDiscovery.isNotEmpty()) {
                    Log.d(TAG, "Triggering discovery retry for ${devicesNeedingDiscovery.size} devices in ${this.javaClass.simpleName}")
                    retryDiscoveryWithBackoff()
                }
                
                // Handle devices with addresses but not connected - need connection
                if (devicesNeedingConnection.isNotEmpty()) {
                    Log.d(TAG, "Triggering connection attempt for ${devicesNeedingConnection.size} devices in ${this.javaClass.simpleName}")
                    tryConnectToAllDevices()
                }
            }
        }
    }

    /**
     * Immediately attempts to connect to all devices and trigger discovery
     */
    private suspend fun tryConnectToAllDevices() {
        Log.d(TAG, "tryConnectToAllDevices() called for ${this.javaClass.simpleName}")
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
        retryDelayMs = 10000L // Reset to 10 seconds
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
