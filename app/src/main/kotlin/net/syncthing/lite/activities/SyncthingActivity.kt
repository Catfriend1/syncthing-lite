package net.syncthing.lite.activities

import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import android.view.LayoutInflater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.syncthing.java.bep.connectionactor.ConnectionInfo
import net.syncthing.java.bep.connectionactor.ConnectionStatus
import net.syncthing.lite.R
import net.syncthing.lite.async.CoroutineActivity
import net.syncthing.lite.databinding.DialogLoadingBinding
import net.syncthing.lite.library.LibraryHandler

abstract class SyncthingActivity : CoroutineActivity() {
    val libraryHandler: LibraryHandler by lazy {
        LibraryHandler(
                context = this@SyncthingActivity
        )
    }
    private var loadingDialog: AlertDialog? = null
    private var snackBar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

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

        libraryHandler.stop()
        loadingDialog?.dismiss()
    }

    open fun onLibraryLoaded() {
        // Ensure all devices are connected when the library is loaded
        // This is important for re-establishing connections after app resume
        // and especially during IntroActivity → MainActivity transition
        
        // Start monitoring connection status to actively re-establish connections
        launch {
            libraryHandler.subscribeToConnectionStatus().collect { connectionInfo ->
                val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }
                
                // Check if any devices have no known addresses and need discovery retry
                val devicesWithoutAddresses = devices.filter { device ->
                    val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                    connection.status == ConnectionStatus.Disconnected && connection.addresses.isEmpty()
                }
                
                if (devicesWithoutAddresses.isNotEmpty()) {
                    // Trigger both discovery retry and connection establishment for devices without addresses
                    libraryHandler.retryDiscoveryForDevicesWithoutAddresses()
                    // Also ensure connection actors are created/active for devices without addresses
                    launch(Dispatchers.IO) {
                        try {
                            libraryHandler.libraryManager.withLibrary { library ->
                                library.syncthingClient.connectToNewlyAddedDevices()
                            }
                        } catch (e: Exception) {
                            // Log error but continue - this is a background operation
                        }
                    }
                }
            }
        }
        
        // Also trigger immediate connection attempt for all devices
        libraryHandler.syncthingClient { syncthingClient ->
            syncthingClient.connectToNewlyAddedDevices()
        }
    }
}
