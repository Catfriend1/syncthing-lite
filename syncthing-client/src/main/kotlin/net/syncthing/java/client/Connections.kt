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
package net.syncthing.java.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.syncthing.java.bep.connectionactor.ConnectionActorWrapper
import net.syncthing.java.bep.connectionactor.ConnectionInfo
import net.syncthing.java.core.beans.DeviceId

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Connections(val generate: (DeviceId) -> ConnectionActorWrapper) {
    private val map = mutableMapOf<DeviceId, ConnectionActorWrapper>()
    private val connectionStatus = MutableStateFlow<Map<DeviceId, ConnectionInfo>>(emptyMap())
    private val connectionStatusLock = Mutex()
    private val job = Job()
    private val scope = CoroutineScope(job)

    fun getByDeviceId(deviceId: DeviceId): ConnectionActorWrapper {
        synchronized(map) {
            val oldEntry = map[deviceId]

            if (oldEntry != null) {
                return oldEntry
            } else {
                val newEntry = generate(deviceId)

                map[deviceId] = newEntry

                scope.launch {
                    newEntry.subscribeToConnectionInfo().collect { status ->
                        connectionStatusLock.withLock {
                            connectionStatus.emit(
                                connectionStatus.value +
                                        mapOf(deviceId to status)
                            )
                        }
                    }
                }

                return newEntry
            }
        }
    }

    fun shutdown() {
        synchronized(map) {
            map.values.forEach { it.shutdown() }
        }

        job.cancel()
    }

    fun reconnectAllConnections() {
        synchronized(map) {
            map.values.forEach { it.reconnect() }
        }
    }

    fun reconnect(deviceId: DeviceId) {
        synchronized(map) {
            map[deviceId]?.reconnect()
        }
    }

    fun subscribeToConnectionStatusMap() = connectionStatus.asStateFlow()
}
