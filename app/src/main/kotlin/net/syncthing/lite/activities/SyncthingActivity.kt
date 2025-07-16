package net.syncthing.lite.activities

import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import android.view.LayoutInflater
import kotlinx.coroutines.launch
import net.syncthing.lite.R
import net.syncthing.lite.async.CoroutineActivity
import net.syncthing.lite.databinding.DialogLoadingBinding
import net.syncthing.lite.library.LibraryHandler
import net.syncthing.lite.library.ConnectionManager

abstract class SyncthingActivity : CoroutineActivity() {
    val libraryHandler: LibraryHandler by lazy {
        LibraryHandler(
                context = this@SyncthingActivity
        )
    }
    private var loadingDialog: AlertDialog? = null
    private var snackBar: Snackbar? = null
    private lateinit var connectionManager: ConnectionManager
    private var isStarted = false

    companion object {
        private const val TAG = "SyncthingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectionManager = ConnectionManager(libraryHandler, this, TAG)
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

        connectionManager.stop()
        libraryHandler.stop()
        loadingDialog?.dismiss()
    }

    open fun onLibraryLoaded() {
        // For MainActivity, enable discovery when library is loaded
        if (this is MainActivity) {
            libraryHandler.enableLocalDiscovery()
            libraryHandler.enableGlobalDiscovery()
        }
        
        // Start connection manager (no conditions for SyncthingActivity)
        connectionManager.start()
        
        // For MainActivity, ensure immediate connection attempt
        if (this is MainActivity) {
            connectionManager.triggerImmediateConnectionAttempt()
        }
    }

    /**
     * Trigger immediate discovery and connection (called when new devices are added)
     */
    fun triggerImmediateConnectionAttempt() {
        connectionManager.triggerImmediateConnectionAttempt()
    }
}
