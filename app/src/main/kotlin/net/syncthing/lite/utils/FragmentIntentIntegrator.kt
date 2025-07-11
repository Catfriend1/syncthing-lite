package net.syncthing.lite.utils

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

import com.google.zxing.integration.android.IntentIntegrator

// https://stackoverflow.com/a/22320076/1837158
class FragmentIntentIntegrator(private val fragment: Fragment) : IntentIntegrator(fragment.activity) {

    private var launcher: ActivityResultLauncher<Intent>? = null

    init {
        // Initialize the activity result launcher
        launcher = fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Handle the result using the IntentIntegrator's parsing method
            val scanResult = IntentIntegrator.parseActivityResult(IntentIntegrator.REQUEST_CODE, result.resultCode, result.data)
            if (scanResult != null) {
                // Trigger the fragment's onActivityResult method to maintain compatibility
                fragment.onActivityResult(IntentIntegrator.REQUEST_CODE, result.resultCode, result.data)
            }
        }
    }

    override fun startActivityForResult(intent: Intent, code: Int) {
        launcher?.launch(intent)
    }
}
