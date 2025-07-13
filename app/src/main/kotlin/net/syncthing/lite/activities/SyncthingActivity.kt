package net.syncthing.lite.activities

import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import android.os.Bundle
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

        // Stop the connection manager
        connectionManagerJob?.cancel()
        connectionManagerJob = null
        
        libraryHandler.stop()
        loadingDialog?.dismiss()
    }

    open fun onLibraryLoaded() {
        // Start the centralized connection manager
        startConnectionManager()
    }

    /**
     * Centralized connection manager that handles discovery and connection establishment
     * with proper backoff strategy and lifecycle management.
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
                
                // Handle devices with addresses but not connected - need connection
                if (devicesNeedingConnection.isNotEmpty()) {
                    tryConnectToAllDevices()
                }
            }
        }
    }

    /**
     * Immediately attempts to connect to all devices and trigger discovery
     */
    private suspend fun tryConnectToAllDevices() {
        launch(Dispatchers.IO) {
            try {
                libraryHandler.libraryManager.withLibrary { library ->
                    library.syncthingClient.connectToNewlyAddedDevices()
                }
            } catch (e: Exception) {
                // Log error but continue - this is a background operation
            }
        }
        
        // Also trigger discovery for devices without addresses
        libraryHandler.retryDiscoveryForDevicesWithoutAddresses()
    }

    /**
     * Retry discovery with exponential backoff strategy
     */
    private suspend fun retryDiscoveryWithBackoff() {
        // Apply exponential backoff
        delay(retryDelayMs)
        
        // Trigger discovery
        libraryHandler.retryDiscoveryForDevicesWithoutAddresses()
        
        // Increase delay for next retry (exponential backoff)
        retryDelayMs = min(retryDelayMs * 2, maxRetryDelayMs)
    }

    /**
     * Reset the retry delay when a successful connection is established
     */
    fun resetRetryDelay() {
        retryDelayMs = 10000L // Reset to 10 seconds
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
