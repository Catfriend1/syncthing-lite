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
package net.syncthing.java.bep.index

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import net.syncthing.java.bep.BlockExchangeProtos
import net.syncthing.java.bep.connectionactor.ClusterConfigInfo
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.exception.ExceptionReport
import net.syncthing.java.core.exception.reportExceptions
import net.syncthing.java.core.interfaces.IndexRepository
import net.syncthing.java.core.interfaces.IndexTransaction
import net.syncthing.java.core.interfaces.TempRepository
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IndexMessageQueueProcessor (
        private val indexRepository: IndexRepository,
        private val tempRepository: TempRepository,
        private val onIndexRecordAcquiredEvents: MutableSharedFlow<IndexInfoUpdateEvent>,
        private val onFullIndexAcquiredEvents: MutableSharedFlow<String>,
        private val onFolderStatsUpdatedEvents: MutableSharedFlow<FolderStatsChangedEvent>,
        private val isRemoteIndexAcquired: (ClusterConfigInfo, DeviceId, IndexTransaction) -> Boolean,
        exceptionReportHandler: (ExceptionReport) -> Unit
) {
    private data class IndexUpdateAction(val update: BlockExchangeProtos.IndexUpdate, val clusterConfigInfo: ClusterConfigInfo, val peerDeviceId: DeviceId)
    private data class StoredIndexUpdateAction(val updateId: String, val clusterConfigInfo: ClusterConfigInfo, val peerDeviceId: DeviceId)

    companion object {
        private val logger = LoggerFactory.getLogger(IndexMessageQueueProcessor::class.java)
        private const val BATCH_SIZE = 128
    }

    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val indexUpdateIncomingLock = Mutex()
    private val indexUpdateProcessStoredQueue = Channel<StoredIndexUpdateAction>(capacity = Channel.UNLIMITED)
    private val indexUpdateProcessingQueue = Channel<IndexUpdateAction>(capacity = Channel.RENDEZVOUS)

    suspend fun handleIndexMessageReceivedEvent(folderId: String, filesList: List<BlockExchangeProtos.FileInfo>, clusterConfigInfo: ClusterConfigInfo, peerDeviceId: DeviceId) {
        filesList.chunked(BATCH_SIZE).forEach { chunk ->
            handleIndexMessageReceivedEventWithoutChunking(folderId, chunk, clusterConfigInfo, peerDeviceId)
        }
    }

    suspend fun handleIndexMessageReceivedEventWithoutChunking(folderId: String, filesList: List<BlockExchangeProtos.FileInfo>, clusterConfigInfo: ClusterConfigInfo, peerDeviceId: DeviceId) {
        indexUpdateIncomingLock.withLock {
            logger.info("Received index message event, preparing to process message.")

            val data = BlockExchangeProtos.IndexUpdate.newBuilder()
                    .addAllFiles(filesList)
                    .setFolder(folderId)
                    .build()

            val result = indexUpdateProcessingQueue.trySend(IndexUpdateAction(data, clusterConfigInfo, peerDeviceId))
            if (result.isSuccess) {
                // message is being processed now
            } else {
                val key = tempRepository.pushTempData(data.toByteArray())

                logger.debug("Received index message event and queued for processing, stored to temporary record {}.", key)
                indexUpdateProcessStoredQueue.send(StoredIndexUpdateAction(key, clusterConfigInfo, peerDeviceId))
            }
        }
    }

    init {
        scope.launch {
            indexUpdateProcessingQueue.consumeEach {
                try {
                    doHandleIndexMessageReceivedEvent(it)
                } catch (ex: IndexMessageProcessor.IndexInfoNotFoundException) {
                    // ignored
                    // this is expected when the data is deleted but some index updates are still in the queue

                    logger.warn("Could not find the index information for the index update.")
                } catch (ex: Exception) {
                    logger.error("💥 Unexpected exception while processing index message: ${ex.message}", ex)
                }
            }
        }.reportExceptions("IndexMessageQueueProcessor.indexUpdateProcessingQueue", exceptionReportHandler)

        scope.launch {
            indexUpdateProcessStoredQueue.consumeEach { action ->
                logger.debug("Processing the index message event from the temporary record {}.", action.updateId)

                val data = tempRepository.popTempData(action.updateId)
                val message = BlockExchangeProtos.IndexUpdate.parseFrom(data)

                indexUpdateProcessingQueue.send(IndexUpdateAction(
                        message,
                        action.clusterConfigInfo,
                        action.peerDeviceId
                ))
            }
        }.reportExceptions("IndexMessageQueueProcessor.indexUpdateProcessStoredQueue", exceptionReportHandler)
    }

    private suspend fun doHandleIndexMessageReceivedEvent(action: IndexUpdateAction) {
        val (message, clusterConfigInfo, peerDeviceId) = action

        logger.debug("📦 IndexUpdate folderId: ${message.folder}, filesCount: ${message.filesCount}")
        /*
        message.filesList.forEachIndexed { i, file ->
            val versionInfo = file.version.countersList.joinToString { "id=${it.id}, value=${it.value}" }
            logger.debug("📄 File[$i]: name=${file.name}, size=${file.size}, type=${file.type}, deleted=${file.deleted}, version=[$versionInfo]")
        }
        */

        val folderId = message.folder
        logger.debug("🔎 Checking folder info for folderId=$folderId")

        val folderInfo = clusterConfigInfo.folderInfoById[folderId]
            ?: throw IllegalStateException("Received folder information for unknown folderId=$folderId")

        if (!folderInfo.isDeviceInSharedFolderWhitelist) {
            throw IllegalStateException("Received index update for a folder which is not shared.")
        }

        logger.info("Processing an index message with {} records.", message.filesCount)

        val (indexResult, wasIndexAcquired) = indexRepository.runInTransaction { indexTransaction ->
            val wasIndexAcquiredBefore = isRemoteIndexAcquired(clusterConfigInfo, peerDeviceId, indexTransaction)

            val startTime = System.currentTimeMillis()

            val indexResult = IndexMessageProcessor.doHandleIndexMessageReceivedEvent(
                    message = message,
                    peerDeviceId = peerDeviceId,
                    transaction = indexTransaction
            )

            val endTime = System.currentTimeMillis()

            logger.info("Processed {} index records, and acquired {} in {} milliseconds",
                    message.filesCount,
                    indexResult.updatedFiles.size,
                    endTime - startTime)

            logger.debug("New Index Information: {}.", indexResult.newIndexInfo)

            indexResult to ((!wasIndexAcquiredBefore) && isRemoteIndexAcquired(clusterConfigInfo, peerDeviceId, indexTransaction))
        }

        if (indexResult.updatedFiles.isNotEmpty()) {
            onIndexRecordAcquiredEvents.tryEmit(IndexRecordAcquiredEvent(message.folder, indexResult.updatedFiles, indexResult.newIndexInfo))
        }

        onFolderStatsUpdatedEvents.tryEmit(FolderStatsUpdatedEvent(indexResult.newFolderStats))

        if (wasIndexAcquired) {
            logger.debug("Index acquired successfully.")
            onFullIndexAcquiredEvents.tryEmit(message.folder)
        }
    }

    fun stop() {
        logger.info("Stopping index record processor.")
        job.cancel()
    }
}
