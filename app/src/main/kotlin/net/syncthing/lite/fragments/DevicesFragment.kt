package net.syncthing.lite.fragments

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.syncthing.java.bep.connectionactor.ConnectionInfo
import net.syncthing.java.core.beans.DeviceInfo
import net.syncthing.lite.R
import net.syncthing.lite.activities.QRScannerActivity
import net.syncthing.lite.adapters.DeviceAdapterListener
import net.syncthing.lite.adapters.DevicesAdapter
import net.syncthing.lite.databinding.FragmentDevicesBinding
import net.syncthing.lite.databinding.ViewEnterDeviceIdBinding
import net.syncthing.lite.databinding.DialogEditDeviceBinding
import net.syncthing.lite.utils.Util
import java.io.IOException

class DevicesFragment : SyncthingFragment() {

    private lateinit var binding: FragmentDevicesBinding
    private val adapter = DevicesAdapter()
    private var addDeviceDialog: AlertDialog? = null
    private var addDeviceDialogBinding: ViewEnterDeviceIdBinding? = null
    private var qrCodeLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        qrCodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val scanResult = result.data?.getStringExtra(QRScannerActivity.SCAN_RESULT)
                if (scanResult != null && scanResult.isNotBlank()) {
                    addDeviceDialogBinding?.deviceId?.setText(scanResult)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = FragmentDevicesBinding.inflate(layoutInflater, container, false)
        binding.addDevice.setOnClickListener { showDialog() }

        binding.list.adapter = adapter

        adapter.listener = object : DeviceAdapterListener {
            override fun onDeviceLongClicked(deviceInfo: DeviceInfo): Boolean {
                // Show context menu with Edit and Delete options
                AlertDialog.Builder(requireContext())
                        .setTitle(deviceInfo.name)
                        .setItems(arrayOf(getString(R.string.edit_device_menu_item), getString(R.string.delete_device_menu_item))) { _, which ->
                            when (which) {
                                0 -> showEditDeviceDialog(deviceInfo) // Edit
                                1 -> showDeleteDeviceDialog(deviceInfo) // Delete
                            }
                        }
                        .show()

                return true
            }
        }

        launch {
            libraryHandler.subscribeToConnectionStatus().collect { connectionInfo ->
                val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }

                adapter.data = devices.map { device -> device to (connectionInfo[device.deviceId] ?: ConnectionInfo.empty) }
                val isEmpty = devices.isEmpty()
                binding.list.visibility = if (isEmpty) View.GONE else View.VISIBLE
                binding.empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            }
        }

        return binding.root
    }

    private fun showDialog() {
        val binding = ViewEnterDeviceIdBinding.inflate(LayoutInflater.from(context), null, false)
        addDeviceDialogBinding = binding

        binding.scanQrCode.setOnClickListener {
            qrCodeLauncher?.let { launcher ->
                val intent = Intent(requireContext(), QRScannerActivity::class.java)
                launcher.launch(intent)
            }
        }
        binding.deviceId.post {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.deviceId, InputMethodManager.SHOW_IMPLICIT)
        }

        val dialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.device_id_dialog_title)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show()

        addDeviceDialog = dialog

        fun handleAddClick() {
            try {
                val deviceId = binding.deviceId.text.toString()
                Util.importDeviceId(libraryHandler.libraryManager, requireContext(), deviceId, { /* TODO: Is updateDeviceList() still required? */ })
                dialog.dismiss()
            } catch (e: IOException) {
                binding.deviceId.error = getString(R.string.invalid_device_id)
            }
        }

        // Use different listener to keep dialog open after button click.
        // https://stackoverflow.com/a/15619098
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)!!.setOnClickListener { handleAddClick() }
    }

    private fun showDeleteDeviceDialog(deviceInfo: DeviceInfo) {
        AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.remove_device_title, deviceInfo.name))
                .setMessage(getString(R.string.remove_device_message, deviceInfo.deviceId.deviceId.substring(0, 7)))
                .setPositiveButton(resources.getText(R.string.yes)) { _, _ ->
                    launch {
                        libraryHandler.libraryManager.withLibrary { library ->
                            library.configuration.update { oldConfig ->
                                val updatedFolders = oldConfig.folders.map { folder ->
                                    folder.copy(
                                        deviceIdWhitelist = folder.deviceIdWhitelist - deviceInfo.deviceId,
                                        deviceIdBlacklist = folder.deviceIdBlacklist - deviceInfo.deviceId,
                                        ignoredDeviceIdList = folder.ignoredDeviceIdList - deviceInfo.deviceId
                                    )
                                }.toSet()

                                oldConfig.copy(
                                    peers = oldConfig.peers.filterNot { it.deviceId == deviceInfo.deviceId }.toSet(),
                                    folders = updatedFolders
                                )
                            }

                            library.configuration.persistLater()
                            library.syncthingClient.disconnect(deviceInfo.deviceId)
                            updateDeviceList()
                        }
                    }
                }
                .setNegativeButton(resources.getText(R.string.no), null)
                .show()
    }

    private fun showEditDeviceDialog(deviceInfo: DeviceInfo) {
        val binding = DialogEditDeviceBinding.inflate(LayoutInflater.from(context), null, false)
        
        // Display the device ID
        binding.deviceIdDisplay.text = deviceInfo.deviceId.deviceId
        
        // Display current addresses as comma-separated string
        val addressesText = if (deviceInfo.addresses.isEmpty()) {
            ""
        } else {
            deviceInfo.addresses.joinToString(", ")
        }
        binding.addressesInput.setText(addressesText)

        val dialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_device_dialog_title)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show()

        fun handleEditSave() {
            try {
                val addressesInputText = binding.addressesInput.text.toString().trim()
                
                // Parse addresses from comma-separated input
                val newAddresses = if (addressesInputText.isEmpty()) {
                    emptyList()
                } else {
                    addressesInputText.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                }

                // Update device with new addresses
                launch {
                    libraryHandler.libraryManager.withLibrary { library ->
                        library.configuration.update { oldConfig ->
                            val updatedPeers = oldConfig.peers.map { peer ->
                                if (peer.deviceId == deviceInfo.deviceId) {
                                    peer.copy(addresses = newAddresses.ifEmpty { listOf("dynamic") })
                                } else {
                                    peer
                                }
                            }.toSet()

                            oldConfig.copy(peers = updatedPeers)
                        }

                        library.configuration.persistLater()
                        
                        // Force recreation of connection actor with updated configuration
                        // First disconnect to clean up and remove old connection actor from map
                        library.syncthingClient.disconnect(deviceInfo.deviceId)
                        // Then trigger creation of new connection actor with updated config
                        library.syncthingClient.connectToNewlyAddedDevices()
                    }
                }
                
                dialog.dismiss()
            } catch (e: Exception) {
                binding.addressesHolder.error = "Error updating device addresses"
            }
        }

        // Use different listener to keep dialog open after button click if there's an error
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)!!.setOnClickListener { handleEditSave() }
    }

    private fun updateDeviceList() {
        launch {
            val connectionInfoMap = libraryHandler.subscribeToConnectionStatus().first()
            val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }

            adapter.data = devices.map { device ->
                device to (connectionInfoMap[device.deviceId] ?: ConnectionInfo.empty)
            }

            val isEmpty = devices.isEmpty()
            binding.list.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.empty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }
    }
}
