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
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.syncthing.java.bep.BlockExchangeProtos
import net.syncthing.java.bep.connectionactor.ClusterConfigInfo
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.exception.ExceptionReport
import net.syncthing.java.core.exception.reportExceptions
import net.syncthing.java.core.interfaces.IndexRepository
import net.syncthing.java.core.interfaces.IndexTransaction
import net.syncthing.java.core.interfaces.TempRepository
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.util.Unbox.box

class IndexMessageQueueProcessor (
        private val indexRepository: IndexRepository,
        private val tempRepository: TempRepository,
        private val onIndexRecordAcquiredEvents: BroadcastChannel<IndexInfoUpdateEvent>,
        private val onFullIndexAcquiredEvents: BroadcastChannel<String>,
        private val onFolderStatsUpdatedEvents: BroadcastChannel<FolderStatsChangedEvent>,
        private val isRemoteIndexAcquired: (ClusterConfigInfo, DeviceId, IndexTransaction) -> Boolean,
        exceptionReportHandler: (ExceptionReport) -> Unit
) {
    private data class IndexUpdateAction(val update: BlockExchangeProtos.IndexUpdate, val clusterConfigInfo: ClusterConfigInfo, val peerDeviceId: DeviceId)
    private data class StoredIndexUpdateAction(val updateId: String, val clusterConfigInfo: ClusterConfigInfo, val peerDeviceId: DeviceId)

    companion object {
        private val LOGGER = LogManager.getLogger(IndexMessageQueueProcessor::class.java)
        private const val BATCH_SIZE = 128
    }

    private val job = Job()
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
            LOGGER.atInfo().log("Received index message event, preparing to process message.")

            val data = BlockExchangeProtos.IndexUpdate.newBuilder()
                    .addAllFiles(filesList)
                    .setFolder(folderId)
                    .build()

            if (indexUpdateProcessingQueue.offer(IndexUpdateAction(data, clusterConfigInfo, peerDeviceId))) {
                // message is being processed now
            } else {
                val key = tempRepository.pushTempData(data.toByteArray())

                LOGGER.atDebug().log("Received index message event and queued for processing, stored to temporary record {}.", key)
                indexUpdateProcessStoredQueue.send(StoredIndexUpdateAction(key, clusterConfigInfo, peerDeviceId))
            }
        }
    }

    init {
        GlobalScope.async(Dispatchers.IO + job) {
            indexUpdateProcessingQueue.consumeEach {
                try {
                    doHandleIndexMessageReceivedEvent(it)
                } catch (ex: IndexMessageProcessor.IndexInfoNotFoundException) {
                    // ignored
                    // this is expected when the data is deleted but some index updates are still in the queue

                    LOGGER.atWarn().log("Could not find the index information for the index update.")
                }
            }
        }.reportExceptions("IndexMessageQueueProcessor.indexUpdateProcessingQueue", exceptionReportHandler)

        GlobalScope.async(Dispatchers.IO + job) {
            indexUpdateProcessStoredQueue.consumeEach { action ->
                LOGGER.atDebug().log("Processing the index message event from the temporary record {}.", action.updateId)

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

        val folderInfo = clusterConfigInfo.folderInfoById[message.folder]
                ?: throw IllegalStateException("Received folder information for folder without known folder information.")

        if (!folderInfo.isDeviceInSharedFolderWhitelist) {
            throw IllegalStateException("Received index update for a folder which is not shared.")
        }

        LOGGER.atInfo().log("Processing an index message with {} records.", box(message.filesCount))

        val (indexResult, wasIndexAcquired) = indexRepository.runInTransaction { indexTransaction ->
            val wasIndexAcquiredBefore = isRemoteIndexAcquired(clusterConfigInfo, peerDeviceId, indexTransaction)

            val startTime = System.currentTimeMillis()

            val indexResult = IndexMessageProcessor.doHandleIndexMessageReceivedEvent(
                    message = message,
                    peerDeviceId = peerDeviceId,
                    transaction = indexTransaction
            )

            val endTime = System.currentTimeMillis()

            LOGGER.atInfo().log("Processed {} index records, and acquired {} in {} milliseconds",
                    box(message.filesCount),
                    box(indexResult.updatedFiles.size),
                    box(endTime - startTime))

            LOGGER.atDebug().log("New Index Information: {}.", indexResult.newIndexInfo)

            indexResult to ((!wasIndexAcquiredBefore) && isRemoteIndexAcquired(clusterConfigInfo, peerDeviceId, indexTransaction))
        }

        if (indexResult.updatedFiles.isNotEmpty()) {
            onIndexRecordAcquiredEvents.send(IndexRecordAcquiredEvent(message.folder, indexResult.updatedFiles, indexResult.newIndexInfo))
        }

        onFolderStatsUpdatedEvents.send(FolderStatsUpdatedEvent(indexResult.newFolderStats))

        if (wasIndexAcquired) {
            LOGGER.atDebug().log("Index acquired successfully.")
            onFullIndexAcquiredEvents.send(message.folder)
        }
    }

    fun stop() {
        LOGGER.atInfo().log("Stopping index record processor.")
        job.cancel()
    }
}
