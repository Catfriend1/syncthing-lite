package net.syncthing.lite.error

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit

object ErrorStorage {
    private const val PREF_KEY = "LAST_ERROR"

    @SuppressLint("ApplySharedPref")
    fun reportError(context: Context, error: String) {
        // this uses commit because the App could be quit directly after that
        context.getSharedPreferences("default", Context.MODE_PRIVATE).edit(commit = true) {
            putString(PREF_KEY, error)
        }
    }

    fun getLastErrorReport(context: Context): String? =
        context.getSharedPreferences("default", Context.MODE_PRIVATE)
            .getString(PREF_KEY, "there is no saved report")
}