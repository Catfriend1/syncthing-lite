package net.syncthing.lite.error

import android.content.Context

object ErrorStorage {
    private const val PREF_KEY = "LAST_ERROR"

    fun reportError(context: Context, error: String) {
        // this uses apply() for better performance
        context.getSharedPreferences("default", Context.MODE_PRIVATE).edit()
            .putString(PREF_KEY, error)
            .apply()
    }

    fun getLastErrorReport(context: Context): String? =
        context.getSharedPreferences("default", Context.MODE_PRIVATE)
            .getString(PREF_KEY, "there is no saved report")
}