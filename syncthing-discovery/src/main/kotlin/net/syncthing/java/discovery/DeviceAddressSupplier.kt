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
package net.syncthing.java.discovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import net.syncthing.java.core.beans.DeviceAddress
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import java.io.Closeable

class DeviceAddressSupplier(private val peerDevices: Set<DeviceId>, private val devicesAddressesManager: DevicesAddressesManager) : Iterable<DeviceAddress?>, Closeable {
    private val deviceAddressListStreams = peerDevices.map { deviceId ->
        devicesAddressesManager.getDeviceAddressManager(deviceId).streamCurrentDeviceAddresses()
    }

    private suspend fun getDeviceAddress(): DeviceAddress? {
        return select {
            deviceAddressListStreams.forEach { stream ->
                stream.onReceive { it }
            }
        }
    }

    suspend fun getDeviceAddressOrWait(timeout: Long) = withTimeout(timeout) {
        getDeviceAddress()
    }

    suspend fun getDeviceAddressOrWait() = getDeviceAddressOrWait(5000L)

    override fun close() {
        deviceAddressListStreams.forEach { it.cancel() }
    }

    @Deprecated(message = "iterator is blocking")
    override fun iterator(): Iterator<DeviceAddress?> {
        return object : Iterator<DeviceAddress?> {
            private var hasNext: Boolean? = null
            private var next: DeviceAddress? = null

            override fun hasNext(): Boolean {
                if (hasNext == null) {
                    try {
                        next = runBlocking {
                            getDeviceAddressOrWait()
                        }
                    } catch (ex: CancellationException) {
                        logger.warn("Cancellation exception occurred because no device address was found.", ex)
                    }

                    hasNext = next != null
                }

                if (hasNext == false) {
                    close()
                }

                return hasNext!!
            }

            override fun next(): DeviceAddress? {
                assert(hasNext())
                val res = next
                hasNext = null
                next = null
                return res
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DeviceAddressSupplier::class.java)
    }
}
