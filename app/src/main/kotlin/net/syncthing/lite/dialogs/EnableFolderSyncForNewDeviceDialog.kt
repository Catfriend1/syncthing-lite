package net.syncthing.lite.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.support.v4.app.FragmentManager
import net.syncthing.java.core.beans.DeviceInfo
import net.syncthing.lite.R
import net.syncthing.lite.fragments.SyncthingDialogFragment
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.Serializable

class EnableFolderSyncForNewDeviceDialog : SyncthingDialogFragment() {

    companion object {
        private const val FOLDER_ID = "folderId"
        private const val FOLDER_NAME = "folderName"
        private const val DEVICES = "devices"
        private const val STATUS_CURRENT_DEVICE_ID = "currentDeviceId"

        private const val TAG = "EnableFolderSyncForNewDeviceDialog"

        fun newInstance(folderId: String, folderName: String, devices: List<DeviceInfo>) =
            EnableFolderSyncForNewDeviceDialog().apply {
                arguments = Bundle().apply {
                    putString(FOLDER_ID, folderId)
                    putString(FOLDER_NAME, folderName)
                    putSerializable(DEVICES, ArrayList(devices))
                }
            }
    }

    private var currentDeviceId = 0
    private lateinit var devices: ArrayList<DeviceInfo>

    @Suppress("UNCHECKED_CAST")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val folderId = arguments?.getString(FOLDER_ID).orEmpty()
        val folderName = arguments?.getString(FOLDER_NAME).orEmpty()

        devices = (arguments?.getSerializable(DEVICES) as? ArrayList<DeviceInfo>) ?: arrayListOf()

        if (savedInstanceState != null) {
            currentDeviceId = savedInstanceState.getInt(STATUS_CURRENT_DEVICE_ID)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_enable_folder_sync_for_new_device_title)
            .setMessage(R.string.dialog_enable_folder_sync_for_new_device_text)
            .setPositiveButton(R.string.dialog_enable_folder_sync_for_new_device_positive, null)
            .setNegativeButton(R.string.dialog_enable_folder_sync_for_new_device_negative, null)
            .create()

        fun bindDeviceId() {
            if (currentDeviceId >= devices.size) {
                dismissAllowingStateLoss()
            } else {
                val device = devices[currentDeviceId]

                dialog.setMessage(
                    getString(
                        R.string.dialog_enable_folder_sync_for_new_device_text,
                        folderName,
                        device.name,
                        device.deviceId.deviceId
                    )
                )

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    GlobalScope.launch {
                        libraryHandler.libraryManager.withLibrary {
                            val oldFolder = it.configuration.folders.find { f -> f.folderId == folderId }!!
                            it.configuration.update { oldConfig ->
                                oldConfig.copy(
                                    folders = oldConfig.folders
                                        .filterNot { it.folderId == folderId }
                                        .toSet() + setOf(
                                        oldFolder.copy(
                                            deviceIdWhitelist = oldFolder.deviceIdWhitelist + device.deviceId,
                                            deviceIdBlacklist = oldFolder.deviceIdBlacklist - device.deviceId
                                        )
                                    )
                                )
                            }
                            it.syncthingClient.reconnect(device.deviceId)
                            it.configuration.persistLater()
                        }
                    }
                    currentDeviceId++
                    bindDeviceId()
                }

                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                    GlobalScope.launch {
                        libraryHandler.libraryManager.withLibrary {
                            val oldFolder = it.configuration.folders.find { f -> f.folderId == folderId }!!
                            it.configuration.update { oldConfig ->
                                oldConfig.copy(
                                    folders = oldConfig.folders
                                        .filterNot { it.folderId == folderId }
                                        .toSet() + setOf(
                                        oldFolder.copy(
                                            ignoredDeviceIdList = oldFolder.deviceIdWhitelist + device.deviceId
                                        )
                                    )
                                )
                            }
                            it.syncthingClient.reconnect(device.deviceId)
                            it.configuration.persistLater()
                        }
                    }
                    currentDeviceId++
                    bindDeviceId()
                }
            }
        }

        dialog.setOnShowListener { bindDeviceId() }

        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATUS_CURRENT_DEVICE_ID, currentDeviceId)
    }

    fun show(fragmentManager: FragmentManager) = show(fragmentManager, TAG)
}
