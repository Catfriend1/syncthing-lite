package net.syncthing.java.bep.folder

import net.syncthing.java.bep.utils.longMaxBy
import net.syncthing.java.core.beans.FolderInfo
import net.syncthing.java.core.beans.FolderStats
import net.syncthing.java.core.beans.IndexInfo

data class FolderStatus(
        val info: FolderInfo,
        val stats: FolderStats,
        val indexInfo: List<IndexInfo>,
        val localDeviceId: String? = null
) {
    companion object {
        fun createDummy(folder: String) = FolderStatus(
                info = FolderInfo(
                        folder,
                        folder,
                        deviceIdBlacklist = emptySet(),
                        deviceIdWhitelist = emptySet(),
                        ignoredDeviceIdList = emptySet()
                ),
                stats = FolderStats.createDummy(folder),
                indexInfo = emptyList(),
                localDeviceId = null
        )
    }

    val missingIndexUpdates: Long by lazy {
        // Filter out the local device from the calculation to only show pending updates from remote devices
        val remoteIndexInfo = localDeviceId?.let { localId ->
            indexInfo.filter { it.deviceId != localId }
        } ?: indexInfo

        Math.max(
                0,
                remoteIndexInfo.longMaxBy ({ it.maxSequence }, 0) -
                        remoteIndexInfo.longMaxBy ({ it.localSequence }, 0)

        )
    }
}
