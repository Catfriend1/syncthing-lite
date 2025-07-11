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
package net.syncthing.java.bep.folder

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.syncthing.java.bep.index.*
import net.syncthing.java.core.beans.FolderStats
import net.syncthing.java.core.configuration.Configuration
import java.io.Closeable

@OptIn(ExperimentalCoroutinesApi::class)
class FolderBrowser internal constructor(
    private val indexHandler: IndexHandler,
    private val configuration: Configuration
) : Closeable {
    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private val foldersStatus = MutableStateFlow<Map<String, FolderStatus>>(emptyMap())

    init {
        scope.launch {
            // get initial status
            val currentFolderStats = mutableMapOf<String, FolderStats>()

            val currentIndexInfo = withContext(Dispatchers.IO) {
                indexHandler.indexRepository.runInTransaction { indexTransaction ->
                    configuration.folders.map { it.folderId }.forEach { folderId ->
                        currentFolderStats[folderId] =
                            indexTransaction.findFolderStats(folderId) ?: FolderStats.createDummy(folderId)
                    }

                    indexTransaction.findAllIndexInfos().groupBy { it.folderId }.toMutableMap()
                }
            }

            // send status
            suspend fun dispatch() {
                foldersStatus.emit(
                        configuration.folders.map { info ->
                            FolderStatus(
                                    info = info,
                                    stats = currentFolderStats[info.folderId]
                                        ?: FolderStats.createDummy(info.folderId),
                                    indexInfo = currentIndexInfo[info.folderId] ?: emptyList()
                            )
                        }.associateBy { it.info.folderId }
                )
            }

            dispatch()

            val updateLock = Mutex()

            launch {
                indexHandler.subscribeFolderStatsUpdatedEvents().collect { event ->
                    updateLock.withLock {
                        when (event) {
                            is FolderStatsUpdatedEvent -> currentFolderStats[event.folderStats.folderId] = event.folderStats
                            FolderStatsResetEvent -> currentFolderStats.clear()
                        }
                        dispatch()
                    }
                }
            }

            launch {
                indexHandler.subscribeToOnIndexUpdateEvents().collect { event ->
                    updateLock.withLock {
                        when (event) {
                            is IndexRecordAcquiredEvent -> {
                                val oldList = currentIndexInfo[event.folderId] ?: emptyList()
                                val newList = oldList
                                    .filter { it.deviceId != event.indexInfo.deviceId } + event.indexInfo

                                currentIndexInfo[event.folderId] = newList
                            }
                            IndexInfoClearedEvent -> currentIndexInfo.clear()
                        }.let { /* require that all cases are handled */ }
                        dispatch()
                    }
                }
            }

            launch {
                configuration.subscribe().collect {
                    dispatch()
                }
            }
        }
    }

    fun folderInfoAndStatusStream(): ReceiveChannel<List<FolderStatus>> = scope.produce {
        foldersStatus.collect { folderStats ->
            send(folderStats.values.sortedBy { it.info.label })
        }
    }

    suspend fun folderInfoAndStatusList(): List<FolderStatus> {
        return folderInfoAndStatusStream().receive()
    }

    suspend fun getFolderStatus(folder: String): FolderStatus {
        return getFolderStatus(folder, foldersStatus.value)
    }

    fun getFolderStatusSync(folder: String): FolderStatus = runBlocking { getFolderStatus(folder) }

    private fun getFolderStatus(folder: String, folderStatus: Map<String, FolderStatus>) =
        folderStatus[folder] ?: FolderStatus.createDummy(folder)

    override fun close() {
        job.cancel()
    }
}
