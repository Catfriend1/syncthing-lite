package net.syncthing.lite.activities

import android.content.res.Configuration;
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
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

    companion object {
        private const val TAG = "SyncthingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Opt-in to edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        insetsController.isAppearanceLightStatusBars = !isDarkMode
        insetsController.isAppearanceLightNavigationBars = !isDarkMode
    }

    override fun onStart() {
        super.onStart()

        val binding = DialogLoadingBinding.inflate(LayoutInflater.from(this))
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
