package net.syncthing.lite.dialogs.downloadfolder

import java.io.Serializable

data class FolderDownloadSpec(
    val folder: String,
    val path: String,
    val folderName: String
) : Serializable