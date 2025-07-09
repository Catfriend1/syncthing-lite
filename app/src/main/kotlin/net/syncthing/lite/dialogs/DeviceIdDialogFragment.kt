package net.syncthing.lite.dialogs

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.*
import net.syncthing.lite.R
import net.syncthing.lite.databinding.DialogDeviceIdBinding
import net.syncthing.lite.fragments.SyncthingDialogFragment
import net.syncthing.lite.library.LibraryHandler
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

class DeviceIdDialogFragment(
    private val libraryHandler: LibraryHandler
) : SyncthingDialogFragment() {

    companion object {
        private const val TAG = "DeviceIdDialog"
        private const val QR_RESOLUTION = 512
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogDeviceIdBinding.inflate(LayoutInflater.from(requireContext()), null, false)

        binding.deviceId.text = "XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX-XXXXXXX"
        binding.deviceId.visibility = View.INVISIBLE

        binding.qrCode.setImageBitmap(
            Bitmap.createBitmap(QR_RESOLUTION, QR_RESOLUTION, Bitmap.Config.RGB_565)
        )

        CoroutineScope(Dispatchers.Main).launch {
            libraryHandler.library { configuration, _, _ ->
                val deviceId = configuration.localDeviceId.deviceId

                withContext(Dispatchers.Main) {
                    binding.deviceId.text = deviceId
                    binding.deviceId.visibility = View.VISIBLE

                    binding.deviceId.setOnClickListener {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(getString(R.string.device_id), deviceId)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), R.string.device_id_copied, Toast.LENGTH_SHORT).show()
                    }

                    binding.share.setOnClickListener {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, deviceId)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.share_device_id_chooser)))
                    }
                }

                // Generate QR code off main thread
                withContext(Dispatchers.Default) {
                    try {
                        val writer = QRCodeWriter()
                        val bitMatrix = writer.encode(deviceId, BarcodeFormat.QR_CODE, QR_RESOLUTION, QR_RESOLUTION)
                        val width = bitMatrix.width
                        val height = bitMatrix.height
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                        for (x in 0 until width) {
                            for (y in 0 until height) {
                                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            binding.flipper.displayedChild = 1
                            binding.qrCode.setImageBitmap(bmp)
                        }
                    } catch (e: WriterException) {
                        Log.w(TAG, "QR Code generation failed", e)
                    }
                }
            }
        }

        return AlertDialog.Builder(requireContext(), theme)
            .setTitle(R.string.device_id)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    fun show(manager: FragmentManager) {
        super.show(manager, TAG)
    }
}
