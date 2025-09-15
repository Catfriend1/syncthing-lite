package net.syncthing.lite.dialogs.downloadfolder

import java.io.File

sealed class FolderDownloadStatus

object FolderDownloadStatusPending : FolderDownloadStatus()

data class FolderDownloadStatusRunning(
    val currentFile: String = "",
    val processedFiles: Int = 0,
    val totalFiles: Int = 0,
    val currentFileProgress: Int = 0
) : FolderDownloadStatus() {
    companion object {
        const val MAX_PROGRESS = 100
    }
}

data class FolderDownloadStatusDone(
    val downloadedFiles: Int,
    val targetFolder: String
) : FolderDownloadStatus()

data class FolderDownloadStatusFailed(
    val error: String
) : FolderDownloadStatus()