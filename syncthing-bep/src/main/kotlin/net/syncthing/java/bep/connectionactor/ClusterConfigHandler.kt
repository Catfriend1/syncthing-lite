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
package net.syncthing.java.bep.connectionactor

import com.google.protobuf.ByteString
import net.syncthing.java.bep.BlockExchangeProtos
import net.syncthing.java.bep.index.IndexHandler
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.beans.FolderInfo
import net.syncthing.java.core.configuration.Configuration
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory

object ClusterConfigHandler {
    private val logger = LoggerFactory.getLogger(ClusterConfigHandler::class.java)

    fun buildClusterConfig(
            configuration: Configuration,
            indexHandler: IndexHandler,
            deviceId: DeviceId
    ): BlockExchangeProtos.ClusterConfig {
        val builder = BlockExchangeProtos.ClusterConfig.newBuilder()

        indexHandler.indexRepository.runInTransaction { indexTransaction ->
            configuration.folders
                    .filter { it.deviceIdWhitelist.contains(deviceId) }
                    .forEach { folder ->
                        val folderBuilder = BlockExchangeProtos.Folder.newBuilder()
                                .setId(folder.folderId)
                                .setLabel(folder.label)

                        // add this device
                        folderBuilder.addDevices(
                                BlockExchangeProtos.Device.newBuilder()
                                        .setId(ByteString.copyFrom(configuration.localDeviceId.toHashData()))
                                        .setIndexId(indexTransaction.getSequencer().indexId())
                                        .setMaxSequence(indexTransaction.getSequencer().currentSequence())
                        )

                        // add other device
                        val indexSequenceInfo = indexTransaction.findIndexInfoByDeviceAndFolder(deviceId, folder.folderId)

                        folderBuilder.addDevices(
                                BlockExchangeProtos.Device.newBuilder()
                                        .setId(ByteString.copyFrom(deviceId.toHashData()))
                                        .apply {
                                            indexSequenceInfo?.let {
                                                indexId = indexSequenceInfo.indexId
                                                maxSequence = indexSequenceInfo.localSequence

                                                logger.info("Send delta index information: Device = {}, Index = {}, Max (Local) Sequence = {}.",
                                                        indexSequenceInfo.deviceId,
                                                        indexSequenceInfo.indexId,
                                                        indexSequenceInfo.localSequence)
                                            }
                                        }
                        )

                        builder.addFolders(folderBuilder)

                        // TODO: add the other devices to the cluster config
                    }
        }

        return builder.build()
    }

    // TODO: understand this
    internal suspend fun handleReceivedClusterConfig(
            clusterConfig: BlockExchangeProtos.ClusterConfig,
            configuration: Configuration,
            otherDeviceId: DeviceId,
            indexHandler: IndexHandler
    ): ClusterConfigInfo {
        val folderInfoList = mutableListOf<ClusterConfigFolderInfo>()
        val newSharedFolders = mutableListOf<FolderInfo>()

        configuration.update { oldConfig ->
            val configFolders = oldConfig.folders.toMutableSet()

            for (folder in clusterConfig.foldersList ?: emptyList()) {
                var folderInfo = ClusterConfigFolderInfo(folder.id, folder.label, isDeviceInSharedFolderWhitelist = false)
                val devicesById = (folder.devicesList ?: emptyList())
                        .associateBy { input ->
                            DeviceId.fromHashData(input.id!!.toByteArray())
                        }
                val otherDevice = devicesById[otherDeviceId]
                val ourDevice = devicesById[configuration.localDeviceId]
                if (otherDevice != null) {
                    folderInfo = folderInfo.copy(isAnnounced = true)
                }
                if (ourDevice != null) {
                    folderInfo = folderInfo.copy(isShared = true)
                    logger.info("Folder {} shared from device {}.", folderInfo, otherDeviceId)

                    val oldFolderEntry = configFolders.find { it.folderId == folderInfo.folderId }

                    if (oldFolderEntry == null) {
                        folderInfo = folderInfo.copy(isDeviceInSharedFolderWhitelist = true)

                        val newFolderInfo = FolderInfo(
                                folderId = folderInfo.folderId,
                                label = folderInfo.label,
                                deviceIdWhitelist = setOf(otherDeviceId),
                                deviceIdBlacklist = emptySet(),
                                ignoredDeviceIdList = emptySet()
                        )

                        configFolders.add(newFolderInfo)
                        newSharedFolders.add(newFolderInfo)
                        logger.info("New folder shared: {}.", folderInfo)
                    } else {
                        if (oldFolderEntry.deviceIdWhitelist.contains(otherDeviceId)) {
                            folderInfo = folderInfo.copy(isDeviceInSharedFolderWhitelist = true)

                            if (oldFolderEntry.label != folderInfo.label) {
                                configFolders.remove(oldFolderEntry)
                                configFolders.add(oldFolderEntry.copy(label = folderInfo.label))
                            }
                        } else {
                            if (!oldFolderEntry.deviceIdBlacklist.contains(otherDeviceId)) {
                                configFolders.remove(oldFolderEntry)
                                configFolders.add(
                                        oldFolderEntry.copy(
                                                deviceIdBlacklist = oldFolderEntry.deviceIdBlacklist + setOf(otherDeviceId)
                                        )
                                )
                            }
                        }
                    }
                } else {
                    logger.info("Folder {} not shared from device {}.", folderInfo, otherDeviceId)
                }

                folderInfoList.add(folderInfo)
            }

            oldConfig.copy(folders = configFolders)
        }

        configuration.folders.forEach {
            logger.debug("🗂 Local Folder: id=${it.folderId}, whitelist=${it.deviceIdWhitelist}, blacklist=${it.deviceIdBlacklist}")
        }

        configuration.persistLater()
        indexHandler.handleClusterConfigMessageProcessedEvent(clusterConfig)

        return ClusterConfigInfo(folderInfoList, newSharedFolders)
    }
}

class ClusterConfigInfo (val folderInfo: List<ClusterConfigFolderInfo>, val newSharedFolders: List<FolderInfo>) {
    companion object {
        val dummy = ClusterConfigInfo(folderInfo = emptyList(), newSharedFolders = emptyList())
    }

    val folderInfoById = folderInfo.associateBy { it.folderId }
    val sharedFolderIds: Set<String> by lazy {
        folderInfo.filter { it.isShared && it.isDeviceInSharedFolderWhitelist }.map { it.folderId }.toSet()
    }
}

data class ClusterConfigFolderInfo(
        val folderId: String,
        val label: String = folderId,
        val isAnnounced: Boolean = false,
        val isShared: Boolean = false,
        val isDeviceInSharedFolderWhitelist: Boolean
) {
    init {
        assert(folderId.isNotEmpty())
    }
}
