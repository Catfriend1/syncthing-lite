package net.syncthing.java.bep.index

import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.beans.IndexInfo
import net.syncthing.java.core.interfaces.IndexTransaction
import org.slf4j.LoggerFactory

object UpdateIndexInfo {
    private val logger = LoggerFactory.getLogger(UpdateIndexInfo::class.java)

    fun updateIndexInfoFromClusterConfig(
            transaction: IndexTransaction,
            folder: String,
            deviceId: DeviceId,
            indexId: Long,
            maxSequence: Long
    ): IndexInfo {
        val oldIndexSequenceInfo = transaction.findIndexInfoByDeviceAndFolder(deviceId, folder)
        logger.debug("🔎 Looked up IndexInfo for device=$deviceId, folder=$folder: $oldIndexSequenceInfo")

        var newIndexSequenceInfo = oldIndexSequenceInfo ?: IndexInfo(
                folderId = folder,
                deviceId = deviceId.deviceId,
                indexId = indexId,
                localSequence = 0,
                maxSequence = -1
        )

        if (indexId != newIndexSequenceInfo.indexId) {
            newIndexSequenceInfo = newIndexSequenceInfo.copy(indexId = indexId)
        }

        if (maxSequence > newIndexSequenceInfo.maxSequence) {
            newIndexSequenceInfo = newIndexSequenceInfo.copy(maxSequence = maxSequence)
        }

        if (oldIndexSequenceInfo != newIndexSequenceInfo) {
            logger.debug("🔄 Updating IndexInfo for device=$deviceId, folder=$folder: $newIndexSequenceInfo")
            transaction.updateIndexInfo(newIndexSequenceInfo)
        } else {
            logger.debug("✅ IndexInfo unchanged for device=$deviceId, folder=$folder")
        }

        return newIndexSequenceInfo
    }

    fun updateIndexInfoFromIndexElementProcessor(
            transaction: IndexTransaction,
            oldIndexInfo: IndexInfo,
            localSequence: Long?
    ): IndexInfo {
        var newIndexSequenceInfo = oldIndexInfo

        if (localSequence != null && localSequence > newIndexSequenceInfo.localSequence) {
            newIndexSequenceInfo = newIndexSequenceInfo.copy(localSequence = localSequence)
        }

        if (oldIndexInfo != newIndexSequenceInfo) {
            logger.debug("📈 Updating IndexInfo sequence: $oldIndexInfo to $newIndexSequenceInfo")
            transaction.updateIndexInfo(newIndexSequenceInfo)
        }

        return newIndexSequenceInfo
    }
}
