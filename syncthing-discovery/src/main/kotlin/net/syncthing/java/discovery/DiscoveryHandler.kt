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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import net.syncthing.java.core.beans.DeviceAddress
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.configuration.Configuration
import net.syncthing.java.core.exception.ExceptionReport
import net.syncthing.java.discovery.protocol.GlobalDiscoveryHandler
import net.syncthing.java.discovery.protocol.LocalDiscoveryHandler
import net.syncthing.java.discovery.utils.AddressRanker
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import java.io.Closeable
import java.util.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.ObsoleteCoroutinesApi::class)
class DiscoveryHandler(
        private val configuration: Configuration,
        exceptionReportHandler: (ExceptionReport) -> Unit
) : Closeable {
    private val globalDiscoveryHandler = GlobalDiscoveryHandler(configuration)
    private val localDiscoveryHandler = LocalDiscoveryHandler(
            configuration,
            exceptionReportHandler,
            { message ->
                logger.info("Received device address list from local discovery.")

                CoroutineScope(Dispatchers.Default).launch {
                    processDeviceAddressBg(message.addresses)
                }
            },
            { deviceId ->
                onMessageFromUnknownDeviceListeners.forEach { listener -> listener(deviceId) }
            }
    )
    val devicesAddressesManager = DevicesAddressesManager()
    private var isClosed = false
    private val onMessageFromUnknownDeviceListeners = Collections.synchronizedSet(HashSet<(DeviceId) -> Unit>())

    private var shouldLoadFromGlobal = true
    private var shouldStartLocalDiscovery = true

    private fun doGlobalDiscoveryIfNotYetDone() {
        // TODO: timeout for reload
        // TODO: retry if connectivity changed

        if (shouldLoadFromGlobal) {
            shouldLoadFromGlobal = false
            CoroutineScope(Dispatchers.Default).launch {
                processDeviceAddressBg(globalDiscoveryHandler.query(configuration.peerIds))
            }
        }
    }

    private fun initLocalDiscoveryIfNotYetDone() {
        if (shouldStartLocalDiscovery) {
            shouldStartLocalDiscovery = false
            localDiscoveryHandler.startListener()
            localDiscoveryHandler.sendAnnounceMessage()
        }
    }

    private suspend fun processDeviceAddressBg(deviceAddresses: Iterable<DeviceAddress>) {
        if (isClosed) {
            logger.debug("Discarding device addresses because discovery handler already closed.")
        } else {
            val list = deviceAddresses.toList()
            val peers = configuration.peerIds
            //do not process address already processed
            list.filter { deviceAddress ->
                !peers.contains(deviceAddress.deviceId)
            }

            AddressRanker.pingAddressesChannel(list).consumeEach {
                putDeviceAddress(it)
            }
        }
    }

    private fun putDeviceAddress(deviceAddress: DeviceAddress) {
        devicesAddressesManager.getDeviceAddressManager(
                deviceId = deviceAddress.deviceId
        ).putAddress(deviceAddress)
    }

    fun newDeviceAddressSupplier(): DeviceAddressSupplier {
        if (isClosed) {
            throw IllegalStateException()
        }

        doGlobalDiscoveryIfNotYetDone()
        initLocalDiscoveryIfNotYetDone()

        return DeviceAddressSupplier(
                peerDevices = configuration.peerIds,
                devicesAddressesManager = devicesAddressesManager
        )
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            localDiscoveryHandler.close()
        }
    }

    fun registerMessageFromUnknownDeviceListener(listener: (DeviceId) -> Unit) {
        onMessageFromUnknownDeviceListeners.add(listener)
    }

    fun unregisterMessageFromUnknownDeviceListener(listener: (DeviceId) -> Unit) {
        onMessageFromUnknownDeviceListeners.remove(listener)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DiscoveryHandler::class.java)
    }
}
