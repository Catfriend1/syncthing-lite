package net.syncthing.lite.library

import android.content.Context
import android.util.Log
import net.syncthing.java.client.SyncthingClient

class DeleteFileTask(
    private val context: Context,
    private val syncthingClient: SyncthingClient,
    private val syncthingFolder: String,
    private val syncthingPath: String
) {
    companion object {
        private const val TAG = "DeleteFileTask"
    }

    suspend fun execute() {
        Log.i(TAG, "Deleting file $syncthingFolder:$syncthingPath")
        
        Log.d(TAG, "Starting delete operation")
        val blockPusher = syncthingClient.getBlockPusher(folderId = syncthingFolder)
        Log.d(TAG, "Got blockPusher, calling pushDelete")
        
        // pushDelete is a suspend function that needs to be awaited
        blockPusher.pushDelete(folderId = syncthingFolder, targetPath = syncthingPath)
        
        Log.d(TAG, "pushDelete completed successfully")
    }
}