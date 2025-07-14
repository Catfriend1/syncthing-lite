package net.syncthing.lite.library

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        Log.i(TAG, "Deleting file $syncthingFolder:$syncthingPath")

        scope.launch {
            try {
                Log.d(TAG, "Starting delete operation in coroutine")
                if (isCancelled) {
                    Log.d(TAG, "Delete operation cancelled before starting")
                    return@launch
                }
                
                val blockPusher = syncthingClient.getBlockPusher(folderId = syncthingFolder)
                Log.d(TAG, "Got blockPusher, calling pushDelete")
                
                // pushDelete is a suspend function that needs to be awaited
                blockPusher.pushDelete(folderId = syncthingFolder, targetPath = syncthingPath)
                
                Log.d(TAG, "pushDelete completed successfully")
                
                if (!isCancelled) {
                    Log.d(TAG, "Posting onComplete callback to main thread")
                    handler.post { 
                        Log.d(TAG, "Calling onComplete callback")
                        onComplete() 
                    }
                } else {
                    Log.d(TAG, "Delete operation was cancelled, not calling onComplete")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Error deleting file", ex)
                if (!isCancelled) {
                    Log.d(TAG, "Posting onError callback to main thread")
                    handler.post { 
                        Log.d(TAG, "Calling onError callback")
                        onError() 
                    }
                }
            }
        }
    }

    fun cancel() {
        Log.d(TAG, "Cancelling delete operation")
        isCancelled = true
        scope.cancel()
    }
}