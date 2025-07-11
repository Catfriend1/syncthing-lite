package net.syncthing.lite.dialogs

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.appcompat.app.AlertDialog
import net.syncthing.lite.R

class ReconnectIssueDialogFragment: DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?) = AlertDialog.Builder(context!!, theme)
        .setMessage(R.string.dialog_warning_reconnect_problem)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            context!!.getSharedPreferences("default", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean(SETTINGS_PARAM, true)
                .apply()
        }
        .create()

    companion object {
        private const val DIALOG_TAG = "ReconnectIssueDialog"
        private const val SETTINGS_PARAM = "has_educated_about_reconnect_issues"

        fun showIfNeeded(activity: FragmentActivity) {
            if (!activity.getSharedPreferences("default", android.content.Context.MODE_PRIVATE)
                    .getBoolean(SETTINGS_PARAM, false)) {
                if (activity.supportFragmentManager.findFragmentByTag(DIALOG_TAG) == null) {
                    ReconnectIssueDialogFragment().show(activity.supportFragmentManager, DIALOG_TAG)
                }
            }
        }
    }
}
