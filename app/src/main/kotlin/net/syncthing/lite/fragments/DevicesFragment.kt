package net.syncthing.lite.fragments

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.zxing.integration.android.IntentIntegrator
import net.syncthing.lite.utils.FragmentIntentIntegrator
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.syncthing.java.bep.connectionactor.ConnectionInfo
import net.syncthing.java.core.beans.DeviceInfo
import net.syncthing.lite.R
import net.syncthing.lite.adapters.DeviceAdapterListener
import net.syncthing.lite.adapters.DevicesAdapter
import net.syncthing.lite.databinding.FragmentDevicesBinding
import net.syncthing.lite.databinding.ViewEnterDeviceIdBinding
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
        // Register the activity result launcher in onCreate
        qrCodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val scanResult = IntentIntegrator.parseActivityResult(IntentIntegrator.REQUEST_CODE, result.resultCode, result.data)
            if (scanResult != null && scanResult.contents != null && scanResult.contents.isNotBlank()) {
                addDeviceDialogBinding?.deviceId?.setText(scanResult.contents)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(layoutInflater, R.layout.fragment_devices, container, false)
        binding.addDevice.setOnClickListener { showDialog() }

        binding.list.adapter = adapter

        adapter.listener = object: DeviceAdapterListener {
            override fun onDeviceLongClicked(deviceInfo: DeviceInfo): Boolean {
                AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.remove_device_title, deviceInfo.name))
                        .setMessage(getString(R.string.remove_device_message, deviceInfo.deviceId.deviceId.substring(0, 7)))
                        .setPositiveButton(resources.getText(R.string.yes)) { _, _ ->
                            launch {
                                libraryHandler.libraryManager.withLibrary { library ->
                                    library.configuration.update { oldConfig ->
                                        oldConfig.copy(
                                                peers = oldConfig.peers
                                                        .filterNot { it.deviceId == deviceInfo.deviceId }
                                                        .toSet()
                                        )
                                    }

                                    library.configuration.persistLater()

                                    // TODO: update the device list (should become a side effect of the call below)
                                    library.syncthingClient.disconnectFromRemovedDevices()
                                }
                            }
                        }
                        .setNegativeButton(resources.getText(R.string.no), null)
                        .show()

                return false
            }
        }

        launch {
            libraryHandler.subscribeToConnectionStatus().collect { connectionInfo ->
                val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }

                adapter.data = devices.map { device -> device to (connectionInfo[device.deviceId] ?: ConnectionInfo.empty) }
                binding.isEmpty = devices.isEmpty()
            }
        }

        return binding.root
    }

    private fun showDialog() {
        val binding = ViewEnterDeviceIdBinding.inflate(LayoutInflater.from(context), null, false)
        addDeviceDialogBinding = binding

        binding.scanQrCode.setOnClickListener {
            qrCodeLauncher?.let { launcher ->
                val integrator = FragmentIntentIntegrator(launcher, activity)
                integrator.initiateScan()
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
}
