package net.syncthing.lite.error

import android.annotation.SuppressLint
import android.content.Context

object ErrorStorage {
    private const val PREF_KEY = "LAST_ERROR"

    @SuppressLint("ApplySharedPref")
    fun reportError(context: Context, error: String) {
        // this uses commit because the App could be quit directly after that
        context.getSharedPreferences("default", Context.MODE_PRIVATE).edit()
            .putString(PREF_KEY, error)
            .commit()
    }

    fun getLastErrorReport(context: Context): String? =
        context.getSharedPreferences("default", Context.MODE_PRIVATE)
            .getString(PREF_KEY, "there is no saved report")
}