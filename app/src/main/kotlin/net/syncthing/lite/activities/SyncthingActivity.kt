package net.syncthing.lite.activities

import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import android.view.LayoutInflater
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

    companion object {
        private const val TAG = "SyncthingActivity"
    }

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
        // nothing to do
    }
}
