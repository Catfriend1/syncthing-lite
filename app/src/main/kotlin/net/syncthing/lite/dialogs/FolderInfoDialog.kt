package net.syncthing.lite.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.FragmentManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatCheckBox
import android.view.LayoutInflater
import kotlinx.coroutines.launch
import net.syncthing.lite.R
import net.syncthing.lite.databinding.DialogFolderInfoBinding
import net.syncthing.lite.fragments.SyncthingDialogFragment

class FolderInfoDialog: SyncthingDialogFragment() {
    companion object {
        fun newInstance(folderId: String) = FolderInfoDialog().apply {
            arguments = Bundle().apply {
                putString(FOLDER_ID, folderId)
            }
        }

        private const val FOLDER_ID = "folderId"
        private const val TAG = "FolderInfoDialog"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val folderId = requireArguments().getString(FOLDER_ID)
        val binding = DialogFolderInfoBinding.inflate(LayoutInflater.from(context))

        val dialog = AlertDialog.Builder(requireContext())
                .setTitle(folderId)
                .setView(binding.root)
                .create()

        launch {
            val configuration = libraryHandler.libraryManager.withLibrary { it.configuration }

            val folderInfo = configuration.folders.find { it.folderId == folderId }

            if (folderInfo == null) {
                dismissAllowingStateLoss()
                return@launch
            }

            dialog.setTitle(folderInfo.label)

            binding.deviceCheckboxesContainer.removeAllViews()

            val allRelatedDevices = (folderInfo.deviceIdWhitelist + folderInfo.deviceIdBlacklist).toSet()

            allRelatedDevices.forEach { deviceId ->
                val deviceInfo = configuration.peers.find { it.deviceId == deviceId }

                val deviceLabel = if (deviceInfo == null)
                    deviceId.deviceId
                else
                    requireContext().getString(R.string.dialog_folder_info_device_list_item, deviceInfo.name, deviceId.deviceId)

                binding.deviceCheckboxesContainer.addView(
                        AppCompatCheckBox(requireContext()).apply {
                            text = deviceLabel
                            isChecked = folderInfo.deviceIdWhitelist.contains(deviceId)

                            setOnCheckedChangeListener { _, isShared ->
                                this@FolderInfoDialog.launch {
                                    libraryHandler.libraryManager.withLibrary { library ->
                                        // update the config
                                        library.configuration.update { oldConfig ->
                                            val oldFolders = oldConfig.folders
                                            var folderToChange = oldFolders.find { it.folderId == folderId }!!
                                            val foldersNotToChange = oldFolders.filterNot { it.folderId == folderId }.toSet()

                                            if (isShared) {
                                                folderToChange = folderToChange.copy(
                                                        ignoredDeviceIdList = folderToChange.ignoredDeviceIdList.filterNot { it == deviceId }.toSet(),
                                                        deviceIdBlacklist = folderToChange.deviceIdBlacklist.filterNot { it == deviceId }.toSet(),
                                                        deviceIdWhitelist = folderToChange.deviceIdWhitelist + setOf(deviceId)
                                                )
                                            } else {
                                                folderToChange = folderToChange.copy(
                                                        deviceIdWhitelist = folderToChange.deviceIdWhitelist.filterNot { it == deviceId }.toSet(),
                                                        deviceIdBlacklist = folderToChange.deviceIdBlacklist + setOf(deviceId),
                                                        ignoredDeviceIdList = folderToChange.ignoredDeviceIdList + setOf(deviceId)
                                                )
                                            }

                                            oldConfig.copy(folders = foldersNotToChange + folderToChange)
                                        }
                                        library.configuration.persistLater()

                                        // apply the change
                                        library.syncthingClient.reconnect(deviceId)
                                    }
                                }
                            }
                        }
                )
            }
        }

        return dialog
    }

    fun show(fragmentManager: FragmentManager) = show(fragmentManager, TAG)
}
