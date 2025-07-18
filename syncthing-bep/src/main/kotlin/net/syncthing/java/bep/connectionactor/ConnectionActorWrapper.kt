/*
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

import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import net.syncthing.java.bep.BlockExchangeProtos
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.exception.ExceptionReport
import net.syncthing.java.core.exception.reportExceptions
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.ObsoleteCoroutinesApi::class)
class ConnectionActorWrapper (
        private val source: ReceiveChannel<Pair<Connection, ConnectionInfo>>,
        val deviceId: DeviceId,
        private val exceptionReportHandler: (ExceptionReport) -> Unit
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)

    private var connection: Connection? = null
    private val connectionInfo = MutableStateFlow<ConnectionInfo>(ConnectionInfo.empty)

    val isConnected: Boolean
        get() = connectionInfo.value.status == ConnectionStatus.Connected

    init {
        // consume updates from the upstream connection generator
        scope.launch {
            source.consumeEach { (connection, info) ->
                this@ConnectionActorWrapper.connection = connection
                this@ConnectionActorWrapper.connectionInfo.emit(info)
            }
        }.reportExceptions("ConnectionActorWrapper(${deviceId.deviceId})", exceptionReportHandler)
    }

    suspend fun sendRequest(request: BlockExchangeProtos.Request) = ConnectionActorUtil.sendRequest(
            request,
            connection?.actor ?: throw IOException("Not connected.")
    )

    suspend fun sendIndexUpdate(update: BlockExchangeProtos.IndexUpdate) = ConnectionActorUtil.sendIndexUpdate(
            update,
            connection?.actor ?: throw IOException("Not connected.")
    )

    fun hasFolder(folderId: String) = connection?.clusterConfigInfo?.sharedFolderIds?.contains(folderId) ?: false

    fun getClusterConfig() = connection?.clusterConfigInfo ?: throw IOException("Not connected.")

    fun shutdown() {
        // closes the scope
        scope.cancel()
    }

    // this triggers a disconnection
    // the ConnectionActorGenerator will reconnect soon
    fun reconnect() {
        val actor = connection?.actor

        scope.launch {
            if (actor != null) {
                ConnectionActorUtil.disconnect(actor)
            }
        }
    }

    fun subscribeToConnectionInfo() = connectionInfo.asStateFlow()
}
