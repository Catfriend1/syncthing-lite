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
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.*
import net.syncthing.lite.R
import net.syncthing.lite.databinding.DialogDeviceIdBinding
import net.syncthing.lite.fragments.SyncthingDialogFragment

class DeviceIdDialogFragment : SyncthingDialogFragment() {

    companion object {
        private const val QR_RESOLUTION = 512
        private const val TAG = "DeviceIdDialog"
    }

    private val scope = MainScope()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogDeviceIdBinding.inflate(LayoutInflater.from(requireContext()), null, false)

        // use a placeholder to prevent size changes; this string is never shown
        binding.deviceId.text = "XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX"
        binding.deviceId.visibility = View.INVISIBLE
        binding.qrCode.setImageBitmap(Bitmap.createBitmap(QR_RESOLUTION, QR_RESOLUTION, Bitmap.Config.RGB_565))

        scope.launch {
            try {
                val (configuration, _, _) = libraryHandler.library()
                val deviceId = configuration.localDeviceId

                // UI Setup
                binding.deviceId.text = deviceId.deviceId
                binding.deviceId.visibility = View.VISIBLE

                binding.deviceId.setOnClickListener {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(getString(R.string.device_id), deviceId.deviceId)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), getString(R.string.device_id_copied), Toast.LENGTH_SHORT).show()
                }

                binding.share.setOnClickListener {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, deviceId.deviceId)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.share_device_id_chooser)))
                }

                // Generate QR code
                val bitmap = withContext(Dispatchers.Default) {
                    generateQrCode(deviceId.deviceId)
                }

                binding.flipper.displayedChild = 1
                binding.qrCode.setImageBitmap(bitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying device ID", e)
            }
        }

        return AlertDialog.Builder(requireContext(), theme)
            .setTitle(getString(R.string.device_id))
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    private fun generateQrCode(text: String): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, QR_RESOLUTION, QR_RESOLUTION)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel() // Cancel coroutine to avoid leaks
    }

    fun show(manager: FragmentManager?) {
        super.show(manager, TAG)
    }
}
