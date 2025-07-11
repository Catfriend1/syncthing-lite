package net.syncthing.lite.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import net.syncthing.lite.R
import net.syncthing.lite.dialogs.ErrorReportDialog
import net.syncthing.lite.error.ErrorStorage
import net.syncthing.lite.library.DefaultLibraryManager

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        val localDeviceName = findPreference("local_device_name") as EditTextPreference
        val appVersion      = findPreference("app_version")
        val forceStop       = findPreference("force_stop")
        val lastCrash       = findPreference("last_crash")
        val reportBug       = findPreference("report_bug")
        val libraryManager  = DefaultLibraryManager.with(context!!)

        MainScope().launch(Dispatchers.Main) {
            libraryManager.withLibrary { library ->
                localDeviceName.text = library.configuration.localDeviceName
            }
        }

        appVersion.summary = context!!.packageManager.getPackageInfo(context!!.packageName, 0)?.versionName ?: ""

        localDeviceName.setOnPreferenceChangeListener { _, _ ->
            val newDeviceName = localDeviceName.text

            MainScope().launch {
                libraryManager.withLibrary { library ->
                    library.configuration.update { it.copy(localDeviceName = newDeviceName) }
                    library.configuration.persistLater()
                }
            }

            true
        }

        forceStop.setOnPreferenceClickListener {
            System.exit(0)

            true
        }

        lastCrash.setOnPreferenceClickListener {
            val errorReport = ErrorStorage.getLastErrorReport(context!!) ?: ""
            ErrorReportDialog.newInstance(errorReport).show(parentFragmentManager)

            true
        }

        reportBug.setOnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/syncthing/syncthing-lite/issues")))

            true
        }
    }
}
