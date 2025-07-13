package net.syncthing.lite.library

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import net.syncthing.java.client.SyncthingClient

class DeleteFileTask(
    private val context: Context,
    private val syncthingClient: SyncthingClient,
    private val syncthingFolder: String,
    private val syncthingPath: String,
    private val onComplete: () -> Unit,
    private val onError: () -> Unit
) {
    companion object {
        private const val TAG = "DeleteFileTask"
        private val handler = Handler(Looper.getMainLooper())
    }

    private var isCancelled = false

    init {
        Log.i(TAG, "Deleting file $syncthingFolder:$syncthingPath")

        MainScope().launch(Dispatchers.IO) {
            try {
                if (isCancelled) return@launch
                
                val blockPusher = syncthingClient.getBlockPusher(folderId = syncthingFolder)
                blockPusher.pushDelete(folderId = syncthingFolder, targetPath = syncthingPath)
                
                handler.post { onComplete() }
            } catch (ex: Exception) {
                Log.e(TAG, "Error deleting file", ex)
                handler.post { onError() }
            }
        }
    }

    fun cancel() {
        isCancelled = true
    }
}