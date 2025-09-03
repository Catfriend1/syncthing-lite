package net.syncthing.java.bep.index

import net.syncthing.java.bep.BlockExchangeProtos
import net.syncthing.java.core.beans.BlockInfo
import net.syncthing.java.core.beans.FileBlocks
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.java.core.exception.ExceptionDetailException
import net.syncthing.java.core.exception.ExceptionDetails
import net.syncthing.java.core.interfaces.IndexTransaction
import org.bouncycastle.util.encoders.Hex
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import java.io.IOException
import java.util.*

object IndexElementProcessor {
    private val logger = LoggerFactory.getLogger(IndexElementProcessor::class.java)

    fun pushRecords(
            transaction: IndexTransaction,
            folder: String,
            updates: List<BlockExchangeProtos.FileInfo>,
            oldRecords: Map<String, FileInfo>,
            folderStatsUpdateCollector: FolderStatsUpdateCollector
    ): List<FileInfo> {
        // this always keeps the last version per path
        val filesToProcess = updates
                .sortedBy { it.sequence }
                .reversed()
                .distinctBy { it.name /* this is the whole path */ }
                .reversed()

        val preparedUpdates = filesToProcess.mapNotNull { prepareUpdate(folder, it) }

        val updatesToApply = preparedUpdates.filter { shouldUpdateRecord(oldRecords[it.first.path], it.first) }

        transaction.updateFileInfoAndBlocks(
                fileInfos = updatesToApply.map { it.first },
                fileBlocks = updatesToApply.mapNotNull { it.second }
        )

        for ((newRecord) in updatesToApply) {
            updateFolderStatsCollector(oldRecords[newRecord.path], newRecord, folderStatsUpdateCollector)
        }

        return updatesToApply.map { it.first }
    }

    fun pushRecord(
            transaction: IndexTransaction,
            folder: String,
            bepFileInfo: BlockExchangeProtos.FileInfo,
            folderStatsUpdateCollector: FolderStatsUpdateCollector,
            oldRecord: FileInfo?
    ): FileInfo? {
        val update = prepareUpdate(folder, bepFileInfo)

        return if (update != null) {
            addRecord(
                    transaction = transaction,
                    newRecord = update.first,
                    fileBlocks = update.second,
                    folderStatsUpdateCollector = folderStatsUpdateCollector,
                    oldRecord = oldRecord
            )
        } else {
            null
        }
    }

    private fun prepareUpdate(
            folder: String,
            bepFileInfo: BlockExchangeProtos.FileInfo
    ): Pair<FileInfo, FileBlocks?>? {
        val builder = FileInfo.Builder()
                .setFolder(folder)
                .setPath(bepFileInfo.name)
                .setLastModified(Date(bepFileInfo.modifiedS * 1000 + bepFileInfo.modifiedNs / 1000000))
                .setVersionList((if (bepFileInfo.hasVersion()) bepFileInfo.version.countersList else null ?: emptyList()).map { record -> FileInfo.Version(record.id, record.value) })
                .setDeleted(bepFileInfo.deleted)

        var fileBlocks: FileBlocks? = null

        when (bepFileInfo.type) {
            BlockExchangeProtos.FileInfoType.FILE -> {
                fileBlocks = FileBlocks(folder, builder.getPath()!!, ((bepFileInfo.blocksList ?: emptyList())).map { record ->
                    BlockInfo(record.offset, record.size, Hex.toHexString(record.hash.toByteArray()))
                })
                builder
                        .setTypeFile()
                        .setHash(fileBlocks.hash)
                        .setSize(bepFileInfo.size)
            }
            BlockExchangeProtos.FileInfoType.DIRECTORY -> builder.setTypeDir()
            else -> {
                logger.warn("Discarding file information due to an unsupported file type: {}.", bepFileInfo.type)
                return null
            }
        }

        return builder.build() to fileBlocks
    }

    private fun shouldUpdateRecord(
            oldRecord: FileInfo?,
            newRecord: FileInfo
    ): Boolean {
        // Always accept if no old record exists
        if (oldRecord == null) {
            return true
        }
        
        // Special case: if we have a deleted record locally but receive a non-deleted record remotely,
        // this represents a file restoration which should always be accepted regardless of timestamps
        if (oldRecord.isDeleted && !newRecord.isDeleted) {
            logger.debug("File restoration detected: {} (local deleted -> remote restored)", newRecord.path)
            logger.debug("Old record (deleted): lastModified={}, version={}", oldRecord.lastModified, oldRecord.versionList)
            logger.debug("New record (restored): lastModified={}, version={}", newRecord.lastModified, newRecord.versionList)
            return true
        }
        
        // Special case: if we have a non-deleted record locally but receive a deleted record remotely,
        // this represents a remote deletion which should be accepted if timestamps allow
        if (!oldRecord.isDeleted && newRecord.isDeleted) {
            logger.debug("Remote deletion detected: {} (local exists -> remote deleted)", newRecord.path)
            logger.debug("Old record (exists): lastModified={}, version={}", oldRecord.lastModified, oldRecord.versionList)
            logger.debug("New record (deleted): lastModified={}, version={}", newRecord.lastModified, newRecord.versionList)
            val shouldAccept = newRecord.lastModified >= oldRecord.lastModified
            logger.debug("Remote deletion accepted: {}", shouldAccept)
            return shouldAccept
        }
        
        // For same deletion state, use timestamp and version comparison
        val shouldAccept = newRecord.lastModified >= oldRecord.lastModified
        logger.trace("Standard record comparison for {}: {} (timestamps: {} >= {})", 
                   newRecord.path, shouldAccept, newRecord.lastModified, oldRecord.lastModified)
        return shouldAccept
    }

    private fun addRecord(
            transaction: IndexTransaction,
            newRecord: FileInfo,
            oldRecord: FileInfo?,
            fileBlocks: FileBlocks?,
            folderStatsUpdateCollector: FolderStatsUpdateCollector
    ): FileInfo? {
        return if (shouldUpdateRecord(oldRecord, newRecord)) {
            logger.trace("Applying record update for: {}. Old: {}, New: {}", 
                       newRecord.path, 
                       oldRecord?.let { "deleted=${it.isDeleted}, lastModified=${it.lastModified}" } ?: "null",
                       "deleted=${newRecord.isDeleted}, lastModified=${newRecord.lastModified}")

            transaction.updateFileInfo(newRecord, fileBlocks)
            updateFolderStatsCollector(oldRecord, newRecord, folderStatsUpdateCollector)

            newRecord
        } else {
            logger.trace("Discarding record update for: {}. Local record is newer - Old: {}, New: {}", 
                       newRecord.path,
                       oldRecord?.let { "deleted=${it.isDeleted}, lastModified=${it.lastModified}" } ?: "null",
                       "deleted=${newRecord.isDeleted}, lastModified=${newRecord.lastModified}")
            null
        }
    }

    private fun updateFolderStatsCollector(
            oldRecord: FileInfo?,
            newRecord: FileInfo,
            folderStatsUpdateCollector: FolderStatsUpdateCollector
    ) {
        val oldMissing = oldRecord == null || oldRecord.isDeleted
        val newMissing = newRecord.isDeleted
        val oldSizeMissing = oldMissing || !oldRecord!!.isFile()
        val newSizeMissing = newMissing || !newRecord.isFile()

        if (!oldSizeMissing) {
            folderStatsUpdateCollector.deltaSize -= oldRecord!!.size!!
        }

        if (!newSizeMissing) {
            folderStatsUpdateCollector.deltaSize += newRecord.size!!
        }

        if (!oldMissing) {
            if (oldRecord!!.isFile()) {
                folderStatsUpdateCollector.deltaFileCount--
            } else if (oldRecord.isDirectory()) {
                folderStatsUpdateCollector.deltaDirCount--
            }
        }

        if (!newMissing) {
            if (newRecord.isFile()) {
                folderStatsUpdateCollector.deltaFileCount++
            } else if (newRecord.isDirectory()) {
                folderStatsUpdateCollector.deltaDirCount++
            }
        }

        folderStatsUpdateCollector.lastModified = newRecord.lastModified
    }
}
