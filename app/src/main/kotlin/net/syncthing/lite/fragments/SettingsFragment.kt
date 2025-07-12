package net.syncthing.lite.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.core.net.toUri
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

        val localDeviceName = findPreference<EditTextPreference>("local_device_name")!!
        val appVersion      = findPreference<Preference>("app_version")!!
        val forceStop       = findPreference<Preference>("force_stop")!!
        val lastCrash       = findPreference<Preference>("last_crash")!!
        val reportBug       = findPreference<Preference>("report_bug")!!
        val libraryManager  = DefaultLibraryManager.with(requireContext())

        MainScope().launch(Dispatchers.Main) {
            libraryManager.withLibrary { library ->
                localDeviceName.text = library.configuration.localDeviceName
            }
        }

        appVersion.summary = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: ""

        localDeviceName.setOnPreferenceChangeListener { _, _ ->
            val newDeviceName = localDeviceName.text

            MainScope().launch {
                libraryManager.withLibrary { library ->
                    library.configuration.update { it.copy(localDeviceName = newDeviceName ?: "") }
                    library.configuration.persistLater()
                }
            }

            true
        }

        forceStop.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            System.exit(0)
            true
        }

        lastCrash.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val errorReport = ErrorStorage.getLastErrorReport(requireContext()) ?: ""
            ErrorReportDialog.newInstance(errorReport).show(parentFragmentManager)
            true
        }

        reportBug.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/Catfriend1/syncthing-lite/issues".toUri()))
            true
        }
    }
}
