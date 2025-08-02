package net.syncthing.lite.library

import android.content.Context
import android.util.Log
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.utils.PathUtils

class RenameFileTask(
    private val context: Context,
    private val syncthingClient: SyncthingClient,
    private val syncthingFolder: String,
    private val syncthingPath: String,
    private val newFileName: String
) {
    companion object {
        private const val TAG = "RenameFileTask"
    }

    suspend fun execute() {
        Log.i(TAG, "Renaming file $syncthingFolder:$syncthingPath to $newFileName")
        
        val blockPusher = syncthingClient.getBlockPusher(folderId = syncthingFolder)
        
        // Calculate the new path
        val parentPath = PathUtils.getParentPath(syncthingPath)
        val newPath = if (PathUtils.isRoot(parentPath)) {
            newFileName
        } else {
            PathUtils.buildPath(parentPath, newFileName)
        }
        
        Log.d(TAG, "New path will be: $newPath")
        
        // Use the pushRename method which handles delete + create internally
        blockPusher.pushRename(syncthingFolder, syncthingPath, newPath)
        
        Log.d(TAG, "Rename operation completed successfully")
    }
}