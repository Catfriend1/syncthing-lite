package net.syncthing.java.bep.index

import net.syncthing.java.bep.BlockExchangeProtos
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.java.core.beans.FolderStats
import net.syncthing.java.core.beans.IndexInfo
import net.syncthing.java.core.interfaces.IndexTransaction
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import org.apache.logging.log4j.util.Unbox.box
import java.lang.RuntimeException

object IndexMessageProcessor {
    private val logger = LoggerFactory.getLogger(IndexMessageProcessor::class.java)

    fun doHandleIndexMessageReceivedEvent(
            message: BlockExchangeProtos.IndexUpdate,
            peerDeviceId: DeviceId,
            transaction: IndexTransaction
    ): Result {
        val folderId = message.folder
        val oldIndexInfo = transaction.findIndexInfoByDeviceAndFolder(peerDeviceId, folderId)
        if (oldIndexInfo == null) {
            logger.warn("⚠️ No IndexInfo found for $peerDeviceId / folder=$folderId")
            throw IndexInfoNotFoundException()
        }

        logger.debug("Processing {} index records for folder ID {}.",
                box(message.filesList.size),
                folderId)

        val oldRecords = transaction.findFileInfo(folderId, message.filesList.map { it.name })
        val folderStatsUpdateCollector = FolderStatsUpdateCollector(message.folder)

        val newRecords = IndexElementProcessor.pushRecords(
                transaction = transaction,
                oldRecords = oldRecords,
                folder = folderId,
                folderStatsUpdateCollector = folderStatsUpdateCollector,
                updates = message.filesList
        )

        val newIndexInfo = if (message.filesList.isEmpty()) {
            oldIndexInfo
        } else {
            var sequence: Long = -1

            for (newRecord in message.filesList) {
                sequence = Math.max(newRecord.sequence, sequence)
            }

            handleFolderStatsUpdate(transaction, folderStatsUpdateCollector)

            UpdateIndexInfo.updateIndexInfoFromIndexElementProcessor(transaction, oldIndexInfo, sequence)
        }

        return Result(newIndexInfo, newRecords.toList(), transaction.findFolderStats(folderId) ?: FolderStats.createDummy(folderId))
    }

    fun handleFolderStatsUpdate(transaction: IndexTransaction, folderStatsUpdateCollector: FolderStatsUpdateCollector) {
        if (folderStatsUpdateCollector.isEmpty()) {
            return
        }

        transaction.updateOrInsertFolderStats(
                folder = folderStatsUpdateCollector.folderId,
                deltaSize = folderStatsUpdateCollector.deltaSize,
                deltaFileCount = folderStatsUpdateCollector.deltaFileCount,
                deltaDirCount = folderStatsUpdateCollector.deltaDirCount,
                lastUpdate = folderStatsUpdateCollector.lastModified
        )
    }

    data class Result(val newIndexInfo: IndexInfo, val updatedFiles: List<FileInfo>, val newFolderStats: FolderStats)
    class IndexInfoNotFoundException: RuntimeException()
}
