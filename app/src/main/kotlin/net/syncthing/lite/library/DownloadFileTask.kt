package net.syncthing.lite.library

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Job
import net.syncthing.java.bep.BlockPullerStatus
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.beans.FileInfo
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DownloadFileTask(private val fileStorageDirectory: File,
                       syncthingClient: SyncthingClient,
                       private val fileInfo: FileInfo,
                       private val onProgress: (status: BlockPullerStatus) -> Unit,
                       private val onComplete: (File) -> Unit,
                       private val onError: (Exception) -> Unit) {

    companion object {
        private const val TAG = "DownloadFileTask"
        private val handler = Handler(Looper.getMainLooper())

        suspend fun downloadFileCoroutine(
                externalCacheDir: File,
                syncthingClient: SyncthingClient,
                fileInfo: FileInfo,
                onProgress: (status: BlockPullerStatus) -> Unit
        ) = suspendCancellableCoroutine<File> {
            continuation ->

            val task = DownloadFileTask(
                    externalCacheDir,
                    syncthingClient,
                    fileInfo,
                    onProgress,
                    {
                        continuation.resume(it)
                    },
                    {
                        continuation.resumeWithException(it)
                    }
            )

            continuation.invokeOnCancellation {
                task.cancel()
            }
        }
    }

    private var downloadJob: Job? = null
    private var doneListenerCalled = false

    init {
        val file = DownloadFilePath(fileStorageDirectory, fileInfo.hash!!)

        downloadJob = MainScope().launch(Dispatchers.IO) {
            if (file.targetFile.exists()) {
                Log.d(TAG, "there is already a file")

                callComplete(file.targetFile)

                return@launch
            }

            try {
                if (!file.filesDirectory.isDirectory) {
                    if (!file.filesDirectory.mkdirs()) {
                        throw IOException("could not create output directory")
                    }
                }

                // download the file to a temp location
                val inputStream = syncthingClient.pullFile(fileInfo, this@DownloadFileTask::callProgress)

                try {
                    FileUtils.copyInputStreamToFile(inputStream, file.tempFile)
                    file.tempFile.renameTo(file.targetFile)
                } finally {
                    file.tempFile.delete()
                }

                Log.i(TAG, "Downloaded file $fileInfo")

                callComplete(file.targetFile)
            } catch (e: Exception) {
                callError(e)

                Log.w(TAG, "Failed to download file $fileInfo", e)
            }
        }
    }

    private fun callProgress(status: BlockPullerStatus) {
        handler.post {
            if (!doneListenerCalled) {
                Log.i("pullFile", "download progress = $status")

                onProgress(status)
            }
        }
    }

    private fun callComplete(file: File) {
        handler.post {
            if (!doneListenerCalled) {
                doneListenerCalled = true

                onComplete(file)
            }
        }
    }

    private fun callError(exception: Exception) {
        handler.post {
            if (!doneListenerCalled) {
                doneListenerCalled = true

                onError(exception)
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        callError(InterruptedException())
    }
}
