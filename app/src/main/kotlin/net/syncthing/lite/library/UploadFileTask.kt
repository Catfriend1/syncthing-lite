package net.syncthing.lite.library

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.syncthing.java.bep.BlockPusher
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.utils.PathUtils
import net.syncthing.lite.utils.Util
import org.apache.commons.io.IOUtils

// TODO: this should be an IntentService with notification
class UploadFileTask(
    context: Context,
    syncthingClient: SyncthingClient,
    localFile: Uri,
    private val syncthingFolder: String,
    syncthingSubFolder: String,
    private val onProgress: (BlockPusher.FileUploadObserver) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: () -> Unit,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "UploadFileTask"
        private val handler = Handler(Looper.getMainLooper())
    }

    private val syncthingPath = if ("" == syncthingSubFolder)
        Util.getContentFileName(context, localFile)
    else
        PathUtils.buildPath(syncthingSubFolder, Util.getContentFileName(context, localFile))

    private val uploadStream = context.contentResolver.openInputStream(localFile)

    private var isCancelled = false
    private var observer: BlockPusher.FileUploadObserver? = null

    init {
        Log.i(TAG, "Uploading file $localFile to folder $syncthingFolder:$syncthingPath")

        scope.launch(Dispatchers.IO) {
            try {
                val input = uploadStream
                if (input == null) {
                    Log.e(TAG, "uploadStream is null for $localFile")
                    handler.post { onError() }
                    return@launch
                }

                val blockPusher = syncthingClient.getBlockPusher(folderId = syncthingFolder)
                observer = blockPusher.pushFile(input, syncthingFolder, syncthingPath)

                handler.post { onProgress(observer!!) }

                while (!observer!!.isCompleted()) {
                    if (isCancelled) {
                        observer?.close()
                        return@launch
                    }

                    observer!!.waitForProgressUpdate()
                    Log.i(TAG, "upload progress = ${observer!!.progressPercentage()}%")
                    handler.post { onProgress(observer!!) }
                }

                IOUtils.closeQuietly(input)
                observer?.close()
                Log.i(TAG, "Upload completed successfully, observer closed")
                handler.post { onComplete() }
            } catch (ex: Exception) {
                Log.e(TAG, "Upload failed", ex)
                observer?.close()
                handler.post { onError() }
            }
        }
    }

    fun cancel() {
        isCancelled = true
        observer?.close()
    }
}
