/*
 * Copyright (C) 2016 Davide Imbriaco
 * Copyright (C) 2018 Jonas Lochmann
 *
 * This Java file is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.syncthing.java.bep

import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.syncthing.java.bep.BlockExchangeProtos.Vector
import net.syncthing.java.bep.connectionactor.ConnectionActorWrapper
import net.syncthing.java.bep.index.*
import net.syncthing.java.core.beans.BlockInfo
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.beans.FileInfo.Version
import net.syncthing.java.core.beans.FolderStats
import net.syncthing.java.core.utils.BlockUtils
import net.syncthing.java.core.utils.NetworkUtils
import org.apache.commons.io.IOUtils
import org.bouncycastle.util.encoders.Hex
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

// TODO: refactor this
class BlockPusher(private val localDeviceId: DeviceId,
                  private val connectionHandler: ConnectionActorWrapper,
                  private val indexHandler: IndexHandler,
                  private val requestHandlerRegistry: RequestHandlerRegistry) {

    suspend fun pushDelete(folderId: String, targetPath: String): BlockExchangeProtos.IndexUpdate {
        val fileInfo = indexHandler.waitForRemoteIndexAcquiredWithTimeout(connectionHandler).getFileInfoByPath(folderId, targetPath)!!
        NetworkUtils.assertProtocol(connectionHandler.hasFolder(fileInfo.folder), {"supplied connection handler $connectionHandler will not share folder ${fileInfo.folder}"})
        return sendIndexUpdate(folderId, BlockExchangeProtos.FileInfo.newBuilder()
                .setName(targetPath)
                .setType(BlockExchangeProtos.FileInfoType.valueOf(fileInfo.type.name))
                .setDeleted(true), fileInfo.versionList)
    }

    suspend fun pushDir(folder: String, path: String): BlockExchangeProtos.IndexUpdate {
        NetworkUtils.assertProtocol(connectionHandler.hasFolder(folder), {"supplied connection handler $connectionHandler will not share folder $folder"})
        return sendIndexUpdate(folder, BlockExchangeProtos.FileInfo.newBuilder()
                .setName(path)
                .setType(BlockExchangeProtos.FileInfoType.DIRECTORY), null)
    }

    suspend fun pushFile(inputStream: InputStream, folderId: String, targetPath: String): FileUploadObserver {
        val fileInfo = indexHandler.waitForRemoteIndexAcquiredWithTimeout(connectionHandler).getFileInfoByPath(folderId, targetPath)
        NetworkUtils.assertProtocol(connectionHandler.hasFolder(folderId), {"supplied connection handler $connectionHandler will not share folder $folderId"})
        assert(fileInfo == null || fileInfo.folder == folderId)
        assert(fileInfo == null || fileInfo.path == targetPath)
        val monitoringProcessExecutorService = Executors.newCachedThreadPool()
        val dataSource = DataSource(inputStream)
        val fileSize = dataSource.size
        val sentBlocks = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        val uploadError = AtomicReference<Exception>()
        val isCompleted = AtomicBoolean(false)
        val updateLock = Object()
        val requestFilter = RequestHandlerFilter(
                deviceId = connectionHandler.deviceId,
                folderId = folderId,
                path = targetPath
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        requestHandlerRegistry.registerListener(requestFilter) { request ->
            scope.async {
                val hash = Hex.toHexString(request.hash.toByteArray())
                logger.debug("Handling block request: {}:{}-{} ({}).",
                        request.name,
                        request.offset,
                        request.size,
                        hash)
                val data = dataSource.getBlock(request.offset, request.size, hash)

                sentBlocks.add(hash)
                synchronized(updateLock) {
                    updateLock.notifyAll()
                }

                BlockExchangeProtos.Response.newBuilder()
                        .setCode(BlockExchangeProtos.ErrorCode.NO_ERROR)
                        .setData(ByteString.copyFrom(data))
                        .setId(request.id)
                        .build()
            }
        }

        logger.debug("Send index update for this file: {}.", targetPath)
        val indexListenerStream = indexHandler.subscribeToOnIndexUpdateEvents()
        scope.launch {
            indexListenerStream.collect { event ->
                if (event is IndexRecordAcquiredEvent) {
                    val (indexFolderId, newRecords, _) = event

                    if (indexFolderId == folderId) {
                        // TODO Rename fileInfo2 (there's also a fileInfo and fileInfo1)
                        for (fileInfo2 in newRecords) {
                            if (fileInfo2.path == targetPath && fileInfo2.hash == dataSource.getHash()) {
                                // TODO check not invalid
                                // sentBlocks.addAll(dataSource.getHashes());
                                isCompleted.set(true)
                                synchronized(updateLock) {
                                    updateLock.notifyAll()
                                }
                            }
                        }
                    }
                }
            }
        }

        val indexUpdate = sendIndexUpdate(folderId, BlockExchangeProtos.FileInfo.newBuilder()
                .setName(targetPath)
                .setSize(fileSize)
                .setType(BlockExchangeProtos.FileInfoType.FILE)
                .addAllBlocks(dataSource.blocks), fileInfo?.versionList)
        return object : FileUploadObserver() {

            override fun progressPercentage() = if (isCompleted.get()) 100 else (sentBlocks.size.toFloat() / dataSource.getHashes().size).toInt()

            // return sentBlocks.size() == dataSource.getHashes().size();
            override fun isCompleted() = isCompleted.get()

            override fun close() {
                logger.debug("Closing the upload process.")
                scope.cancel()
                monitoringProcessExecutorService.shutdown()
                requestHandlerRegistry.unregisterListener(requestFilter)
                
                // Close the input stream and clean up data source
                IOUtils.closeQuietly(inputStream)
                dataSource.close()
                
                //TODO: Rename fileInfo1 and fileInfo
                val (fileInfo1, folderStatsUpdate) = indexHandler.indexRepository.runInTransaction {
                    val folderStatsUpdateCollector = FolderStatsUpdateCollector(folderId)

                    // TODO: notify the IndexBrowsers again (as it was earlier)
                    val builtFileInfo = IndexElementProcessor.pushRecord(
                            it,
                            indexUpdate.folder,
                            indexUpdate.filesList.single(),
                            folderStatsUpdateCollector,
                            it.findFileInfo(folderId, indexUpdate.filesList.single().name)
                    )

                    IndexMessageProcessor.handleFolderStatsUpdate(it, folderStatsUpdateCollector)
                    val folderStatsUpdate = it.findFolderStats(folderId) ?: FolderStats.createDummy(folderId)

                    builtFileInfo to folderStatsUpdate
                }

                runBlocking { indexHandler.sendFolderStatsUpdate(folderStatsUpdate) }
                logger.info("Sent file information record = {}.", fileInfo1)
            }

            @Throws(InterruptedException::class, IOException::class)
            override fun waitForProgressUpdate(): Int {
                synchronized(updateLock) {
                    updateLock.wait()
                }
                if (uploadError.get() != null) {
                    throw IOException(uploadError.get())
                }
                return progressPercentage()
            }

        }
    }

    private suspend fun sendIndexUpdate(folderId: String, fileInfoBuilder: BlockExchangeProtos.FileInfo.Builder,
                                        oldVersions: Iterable<Version>?): BlockExchangeProtos.IndexUpdate {
        run {
            val nextSequence = indexHandler.getNextSequenceNumber()
            val oldVersionsList = oldVersions ?: emptyList()
            logger.debug("File Version List: {}.", oldVersionsList)
            val id = ByteBuffer.wrap(localDeviceId.toHashData()).long
            val newVersion = BlockExchangeProtos.Counter.newBuilder()
                    .setId(id)
                    .setValue(nextSequence)
                    .build()
            logger.debug("Append new version to index. New version: {}.", newVersion)
            fileInfoBuilder
                    .setSequence(nextSequence)
                    .setVersion(Vector.newBuilder().addAllCounters(oldVersionsList.map { record ->
                        BlockExchangeProtos.Counter.newBuilder().setId(record.id).setValue(record.value).build()
                    })
                            .addCounters(newVersion))
        }
        val lastModified = Date()
        val builtFileInfo  = fileInfoBuilder
                .setModifiedS(lastModified.time / 1000)
                .setModifiedNs((lastModified.time % 1000 * 1000000).toInt())
                .setNoPermissions(true)
                .build()
        val indexUpdate = BlockExchangeProtos.IndexUpdate.newBuilder()
                .setFolder(folderId)
                .addFiles(builtFileInfo )
                .build()
        // logger.trace("Update index with file information. File info: {}.", builtFileInfo)
        logger.debug("Update index with file information.")

        connectionHandler.sendIndexUpdate(indexUpdate)

        return indexUpdate
    }

    abstract inner class FileUploadObserver : Closeable {

        abstract fun progressPercentage(): Int

        abstract fun isCompleted(): Boolean

        @Throws(InterruptedException::class)
        abstract fun waitForProgressUpdate(): Int

        @Throws(InterruptedException::class)
        fun waitForComplete(): FileUploadObserver {
            while (!isCompleted()) {
                waitForProgressUpdate()
            }
            return this
        }
    }

    private class DataSource @Throws(IOException::class) constructor(private val inputStream: InputStream) {

        var size: Long = 0
            private set
        var blocks: List<BlockExchangeProtos.BlockInfo> = emptyList()
            private set
        private var hashes: Set<String>? = null

        private var hash: String? = null
        private var fileData: ByteArray? = null

        init {
            // Read the entire file into memory to calculate blocks and be able to serve them later
            val fileDataBuffer = IOUtils.toByteArray(inputStream)
            fileData = fileDataBuffer
            
            val list = mutableListOf<BlockExchangeProtos.BlockInfo>()
            var offset: Long = 0
            var dataOffset = 0
            
            while (dataOffset < fileDataBuffer.size) {
                val blockSize = minOf(BLOCK_SIZE, fileDataBuffer.size - dataOffset)
                val block = fileDataBuffer.copyOfRange(dataOffset, dataOffset + blockSize)
                
                val hash = MessageDigest.getInstance("SHA-256").digest(block)
                list.add(BlockExchangeProtos.BlockInfo.newBuilder()
                        .setHash(ByteString.copyFrom(hash))
                        .setOffset(offset)
                        .setSize(blockSize)
                        .build())
                offset += blockSize.toLong()
                dataOffset += blockSize
            }
            size = offset
            blocks = list
        }

        @Throws(IOException::class)
        fun getBlock(offset: Long, size: Int, hash: String): ByteArray {
            val buffer = fileData?.copyOfRange(offset.toInt(), offset.toInt() + size)
                    ?: throw IOException("File data not available")
            
            NetworkUtils.assertProtocol(Hex.toHexString(MessageDigest.getInstance("SHA-256").digest(buffer)) == hash, {"block hash mismatch!"})
            return buffer
        }


        fun getHashes(): Set<String> {
            return hashes ?: let {
                val hashes2 = blocks.map { input -> Hex.toHexString(input.hash.toByteArray()) }.toSet()
                hashes = hashes2
                return hashes2
            }
        }

        fun getHash(): String {
            return hash ?: let {
                val blockInfo = blocks.map { input ->
                    BlockInfo(input.offset, input.size, Hex.toHexString(input.hash.toByteArray()))
                }
                val hash2 = BlockUtils.hashBlocks(blockInfo)
                hash = hash2
                hash2
            }
        }
        
        fun close() {
            // Clear file data to free memory
            fileData = null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BlockPusher::class.java)
        const val BLOCK_SIZE = 128 * 1024
    }

}
