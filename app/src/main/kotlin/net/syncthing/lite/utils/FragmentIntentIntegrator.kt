package net.syncthing.lite.utils

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher

import com.google.zxing.integration.android.IntentIntegrator

// https://stackoverflow.com/a/22320076/1837158
class FragmentIntentIntegrator(
    private val launcher: ActivityResultLauncher<Intent>,
    activity: android.app.Activity?
) : IntentIntegrator(activity) {

    override fun startActivityForResult(intent: Intent, code: Int) {
        launcher.launch(intent)
    }
}
