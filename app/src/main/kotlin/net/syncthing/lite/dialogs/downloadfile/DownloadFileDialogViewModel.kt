package net.syncthing.lite.dialogs.downloadfile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import net.syncthing.lite.library.DownloadFileTask
import net.syncthing.lite.library.LibraryHandler
import org.apache.commons.io.FileUtils
import java.io.File

class DownloadFileDialogViewModel : ViewModel() {
    companion object {
        private const val TAG = "DownloadFileDialog"
    }

    private var isInitialized = false
    private val statusInternal = MutableLiveData<DownloadFileStatus>()
    private var downloadJob: Job? = null
    val status: LiveData<DownloadFileStatus> = statusInternal

    fun init(
            libraryHandler: LibraryHandler,
            fileSpec: DownloadFileSpec,
            externalCacheDir: File,
            outputUri: Uri?,
            contentResolver: ContentResolver
    ) {
        if (isInitialized) {
            return
        }

        isInitialized = true

        libraryHandler.start()

        // this keeps the client only active as long as the block is running
        // but the file downloading is not synchronous.
        // Due to that, the start and stop calls are used.
        libraryHandler.syncthingClient {
            syncthingClient ->

            try {
                val fileInfo = syncthingClient.indexHandler.getFileInfoByPath(
                        folder = fileSpec.folder,
                        path = fileSpec.path
                )!!

                val task = DownloadFileTask(
                        fileStorageDirectory = externalCacheDir,
                        syncthingClient = syncthingClient,
                        fileInfo = fileInfo,
                        onProgress = { status ->
                            val newProgress = if (status.totalTransferSize == 0L) {
                                // For 0-byte files, show 100% progress immediately
                                DownloadFileStatusRunning.MAX_PROGRESS
                            } else {
                                (status.downloadedBytes * DownloadFileStatusRunning.MAX_PROGRESS / status.totalTransferSize).toInt()
                            }
                            val currentStatus = statusInternal.value

                            // only update if it changed
                            if (!(currentStatus is DownloadFileStatusRunning) || currentStatus.progress != newProgress) {
                                statusInternal.value = DownloadFileStatusRunning(newProgress)
                            }
                        },
                        onComplete = { file ->
                            libraryHandler.stop()

                            downloadJob = MainScope().launch {
                                try {
                                    if (outputUri != null) {
                                        contentResolver.openOutputStream(outputUri).use { outputStream ->
                                            FileUtils.copyFile(file, outputStream)
                                        }
                                    }

                                    statusInternal.postValue(DownloadFileStatusDone(file))
                                } catch (ex: Exception) {
                                    Log.w(TAG, "downloading file failed", ex)
                                    statusInternal.postValue(DownloadFileStatusFailed)
                                }
                            }
                        },
                        onError = {
                            statusInternal.value = DownloadFileStatusFailed

                            libraryHandler.stop()
                        }
                )
            } catch (ex: Exception) {
                Log.w(TAG, "downloading file failed", ex)
                statusInternal.postValue(DownloadFileStatusFailed)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        cancel()
    }

    fun cancel() {
        downloadJob?.cancel()
    }
}
