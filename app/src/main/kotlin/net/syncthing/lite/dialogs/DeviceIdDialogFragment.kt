package net.syncthing.lite.dialogs

import androidx.appcompat.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.FragmentManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.syncthing.lite.R
import net.syncthing.lite.databinding.DialogDeviceIdBinding
import net.syncthing.lite.fragments.SyncthingDialogFragment

class DeviceIdDialogFragment : SyncthingDialogFragment() {

    companion object {
        private const val QR_RESOLUTION = 512
        private const val TAG = "DeviceIdDialog"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogDeviceIdBinding.inflate(LayoutInflater.from(context), null, false)

        // Placeholder to prevent size changes; this string is never shown
        binding.deviceId.text = getString(R.string.device_id_placeholder)
        binding.deviceId.visibility = View.INVISIBLE

        binding.qrCode.setImageBitmap(createBitmap(QR_RESOLUTION, QR_RESOLUTION, Bitmap.Config.RGB_565))

        MainScope().launch {
            libraryHandler.library { configuration, _, _ ->
                val deviceId = configuration.localDeviceId

                withContext(Dispatchers.Main) {
                    binding.deviceId.text = deviceId.deviceId
                    binding.deviceId.visibility = View.VISIBLE

                    binding.deviceId.setOnClickListener { copyDeviceId(deviceId.deviceId) }
                    binding.share.setOnClickListener { shareDeviceId(deviceId.deviceId) }
                }

                // Generate QR code off the main thread
                val qrCodeBitmap = generateQrCode(deviceId.deviceId)

                withContext(Dispatchers.Main) {
                    binding.flipper.displayedChild = 1
                    binding.qrCode.setImageBitmap(qrCodeBitmap)
                }
            }
        }

        return AlertDialog.Builder(requireContext(), theme)
            .setTitle(getString(R.string.device_id))
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    private fun copyDeviceId(deviceId: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.device_id), deviceId)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(requireContext(), getString(R.string.device_id_copied), Toast.LENGTH_SHORT).show()
    }

    private fun shareDeviceId(deviceId: String) {
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, deviceId)
            },
            getString(R.string.share_device_id_chooser)
        ))
    }

    private suspend fun generateQrCode(data: String): Bitmap = withContext(Dispatchers.Default) {
        val writer = QRCodeWriter()
        try {
            val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, QR_RESOLUTION, QR_RESOLUTION)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp[x, y] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            bmp
        } catch (e: WriterException) {
            Log.w(TAG, "QR Code generation failed", e)
            createBitmap(QR_RESOLUTION, QR_RESOLUTION, Bitmap.Config.RGB_565)
        }
    }

    fun show(manager: FragmentManager) {
        super.show(manager, TAG)
    }
}