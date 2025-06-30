package net.syncthing.lite.dialogs

import android.os.Bundle
import androidx.app.DialogFragment
import androidx.app.FragmentActivity
import androidx.app.AlertDialog
import net.syncthing.lite.R
import org.jetbrains.anko.defaultSharedPreferences

class ReconnectIssueDialogFragment: DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?) = AlertDialog.Builder(context!!, theme)
            .setMessage(R.string.dialog_warning_reconnect_problem)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                context!!.defaultSharedPreferences.edit()
                        .putBoolean(SETTINGS_PARAM, true)
                        .apply()
            }
            .create()

    companion object {
        private const val DIALOG_TAG = "ReconnectIssueDialog"
        private const val SETTINGS_PARAM = "has_educated_about_reconnect_issues"

        fun showIfNeeded(activity: FragmentActivity) {
            if (!activity.defaultSharedPreferences.getBoolean(SETTINGS_PARAM, false)) {
                if (activity.supportFragmentManager.findFragmentByTag(DIALOG_TAG) == null) {
                    ReconnectIssueDialogFragment().show(activity.supportFragmentManager, DIALOG_TAG)
                }
            }
        }
    }
}
