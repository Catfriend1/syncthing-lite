package net.syncthing.lite.error

import android.content.Context
import androidx.preference.PreferenceManager

object ErrorStorage {
    private const val PREF_KEY = "last_error"

    fun reportError(context: Context, error: String) {
        // this uses commit because the App could be quit directly after that
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PREF_KEY, error)
            .commit()
    }

    fun getLastErrorReport(context: Context): String? =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_KEY, "there is no saved report")
}