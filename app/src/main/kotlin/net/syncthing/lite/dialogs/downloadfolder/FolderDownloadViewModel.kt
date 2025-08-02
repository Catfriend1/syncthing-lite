package net.syncthing.lite.dialogs.downloadfolder

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.syncthing.java.bep.index.browser.DirectoryContentListing
import net.syncthing.java.bep.index.browser.DirectoryNotFoundListing
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.lite.library.LibraryHandler
import net.syncthing.lite.library.DownloadFileTask
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.*

class FolderDownloadViewModel : ViewModel() {
    companion object {
        private const val TAG = "FolderDownloadViewModel"
    }

    private var isInitialized = false
    private val statusInternal = MutableLiveData<FolderDownloadStatus>()
    private var downloadJob: Job? = null
    val status: LiveData<FolderDownloadStatus> = statusInternal

    fun init(
        libraryHandler: LibraryHandler,
        folderSpec: FolderDownloadSpec,
        externalCacheDir: File,
        targetUri: Uri,
        contentResolver: ContentResolver,
        context: android.content.Context
    ) {
        if (isInitialized) {
            return
        }

        isInitialized = true
        statusInternal.value = FolderDownloadStatusPending

        libraryHandler.start()

        downloadJob = MainScope().launch(Dispatchers.IO) {
            try {
                // Create DocumentFile for the target directory
                val targetDocumentFile = DocumentFile.fromTreeUri(context, targetUri)
                if (targetDocumentFile == null) {
                    withContext(Dispatchers.Main) {
                        statusInternal.value = FolderDownloadStatusFailed("Invalid target directory")
                    }
                    return@launch
                }

                libraryHandler.syncthingClient { syncthingClient ->
                    try {
                        // Get all files in the folder recursively
                        val allFiles = mutableListOf<FileInfo>()
                        collectFilesRecursively(
                            syncthingClient.indexHandler.indexBrowser,
                            folderSpec.folder,
                            folderSpec.path,
                            allFiles
                        )

                        val totalFiles = allFiles.size
                        Log.d(TAG, "Found $totalFiles files to download in folder ${folderSpec.folderName}")

                        // Create the root folder in the target directory
                        val rootFolder = targetDocumentFile.createDirectory(folderSpec.folderName)
                        if (rootFolder == null) {
                            withContext(Dispatchers.Main) {
                                statusInternal.value = FolderDownloadStatusFailed("Failed to create target folder")
                            }
                            return@syncthingClient
                        }

                        // Download each file
                        var processedFiles = 0
                        for (fileInfo in allFiles) {
                            if (downloadJob?.isCancelled == true) {
                                break
                            }

                            try {
                                // Update status
                                withContext(Dispatchers.Main) {
                                    statusInternal.value = FolderDownloadStatusRunning(
                                        currentFile = fileInfo.fileName,
                                        processedFiles = processedFiles,
                                        totalFiles = totalFiles,
                                        currentFileProgress = 0
                                    )
                                }

                                // Download the file
                                val downloadedFile = DownloadFileTask.downloadFileCoroutine(
                                    externalCacheDir,
                                    syncthingClient,
                                    fileInfo
                                ) { status ->
                                    val progress = if (status.totalTransferSize == 0L) {
                                        FolderDownloadStatusRunning.MAX_PROGRESS
                                    } else {
                                        (status.downloadedBytes * FolderDownloadStatusRunning.MAX_PROGRESS / status.totalTransferSize).toInt()
                                    }
                                    
                                    MainScope().launch {
                                        statusInternal.value = FolderDownloadStatusRunning(
                                            currentFile = fileInfo.fileName,
                                            processedFiles = processedFiles,
                                            totalFiles = totalFiles,
                                            currentFileProgress = progress
                                        )
                                    }
                                }

                                // Create the directory structure and copy the file
                                val relativePath = getRelativePath(folderSpec.path, fileInfo.path)
                                val targetFile = createFileInTarget(rootFolder, relativePath, contentResolver)
                                
                                if (targetFile != null) {
                                    contentResolver.openOutputStream(targetFile.uri).use { outputStream ->
                                        FileUtils.copyFile(downloadedFile, outputStream)
                                    }
                                    processedFiles++
                                } else {
                                    Log.w(TAG, "Failed to create target file for ${fileInfo.path}")
                                }

                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to download file ${fileInfo.path}", e)
                                // Continue with other files
                            }
                        }

                        withContext(Dispatchers.Main) {
                            statusInternal.value = FolderDownloadStatusDone(
                                downloadedFiles = processedFiles,
                                targetFolder = folderSpec.folderName
                            )
                        }

                    } catch (e: Exception) {
                        Log.w(TAG, "Folder download failed", e)
                        withContext(Dispatchers.Main) {
                            statusInternal.value = FolderDownloadStatusFailed(e.message ?: "Unknown error")
                        }
                    } finally {
                        libraryHandler.stop()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Folder download failed", e)
                withContext(Dispatchers.Main) {
                    statusInternal.value = FolderDownloadStatusFailed(e.message ?: "Unknown error")
                }
                libraryHandler.stop()
            }
        }
    }

    private suspend fun collectFilesRecursively(
        indexBrowser: net.syncthing.java.bep.index.browser.IndexBrowser,
        folder: String,
        path: String,
        result: MutableList<FileInfo>
    ) {
        val listing = indexBrowser.getDirectoryListing(folder, path)
        
        if (listing is DirectoryContentListing) {
            for (entry in listing.entries) {
                if (entry.type == FileInfo.FileType.FILE) {
                    result.add(entry)
                } else if (entry.type == FileInfo.FileType.DIRECTORY) {
                    // Recursively collect files from subdirectories
                    collectFilesRecursively(indexBrowser, folder, entry.path, result)
                }
            }
        }
    }

    private fun getRelativePath(basePath: String, fullPath: String): String {
        return if (fullPath.startsWith(basePath)) {
            val relative = fullPath.substring(basePath.length)
            if (relative.startsWith("/")) relative.substring(1) else relative
        } else {
            fullPath
        }
    }

    private fun createFileInTarget(
        rootFolder: DocumentFile,
        relativePath: String,
        contentResolver: ContentResolver
    ): DocumentFile? {
        val pathParts = relativePath.split("/").filter { it.isNotEmpty() }
        if (pathParts.isEmpty()) return null

        var currentFolder = rootFolder
        
        // Create directory structure
        for (i in 0 until pathParts.size - 1) {
            val dirName = pathParts[i]
            var subFolder = currentFolder.findFile(dirName)
            if (subFolder == null || !subFolder.isDirectory) {
                subFolder = currentFolder.createDirectory(dirName)
            }
            if (subFolder == null) {
                return null
            }
            currentFolder = subFolder
        }

        // Create the file
        val fileName = pathParts.last()
        return currentFolder.createFile("application/octet-stream", fileName)
    }

    override fun onCleared() {
        super.onCleared()
        cancel()
    }

    fun cancel() {
        downloadJob?.cancel()
    }
}