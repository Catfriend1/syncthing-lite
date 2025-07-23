package net.syncthing.lite.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.beans.DeviceInfo
import net.syncthing.lite.R
import net.syncthing.lite.library.LibraryManager
import java.io.IOException
import java.security.InvalidParameterException
import java.util.Locale
import android.widget.Toast

object Util {

    private fun capitalizeCompat(input: String): String {
        return if (input.isNotEmpty()) {
            input.substring(0, 1).uppercase(Locale.getDefault()) + input.substring(1)
        } else {
            input
        }
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER ?: ""
        val model = Build.MODEL ?: ""
        val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) {
            capitalizeCompat(model)
        } else {
            capitalizeCompat(manufacturer) + " " + model
        }
        return if (deviceName.isBlank()) "android" else deviceName
    }

    fun getContentFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null, null).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                throw InvalidParameterException("Cursor is null or empty")
            }
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex == -1) {
                throw InvalidParameterException("Display name column not found")
            }
            return cursor.getString(columnIndex)
        }
    }

    @Throws(IOException::class)
    fun importDeviceId(
        libraryManager: LibraryManager,
        context: Context,
        deviceId: String,
        onComplete: () -> Unit
    ) {
        val newDeviceId = DeviceId(deviceId.uppercase(Locale.getDefault()))

        MainScope().launch(Dispatchers.Main) {
            libraryManager.withLibrary { library ->
                val didAddDevice = library.configuration.update { oldConfig ->
                    if (oldConfig.peers.any { it.deviceId == newDeviceId }) {
                        oldConfig
                    } else {
                        oldConfig.copy(
                            peers = oldConfig.peers + DeviceInfo(newDeviceId, newDeviceId.shortId)
                        )
                    }
                }

                if (didAddDevice) {
                    library.configuration.persistLater()
                    library.syncthingClient.retryDiscovery()
                    library.syncthingClient.connectToNewlyAddedDevices()

                    Toast.makeText(
                        context,
                        context.getString(R.string.device_import_success, newDeviceId.shortId),
                        Toast.LENGTH_SHORT
                    ).show()
                    onComplete()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.device_already_known, newDeviceId.shortId),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
