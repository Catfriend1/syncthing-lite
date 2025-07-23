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
        if (!localDiscoveryEnabled) {
            logger.trace("initLocalDiscoveryIfNotYetDone() skipped")
            return
        }
        
        if (shouldStartLocalDiscovery) {
            shouldStartLocalDiscovery = false
            // logger.trace("Starting local discovery listener and sending announcement")
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

    /**
     * Enable discovery to start running. This must be called before discovery will actually start.
     */
    fun enableDiscovery() {
        logger.info("enableDiscovery() called - discovery is now enabled")
        discoveryEnabled = true
    }

    /**
     * Disable discovery to prevent it from running
     */
    fun disableDiscovery() {
        logger.info("disableDiscovery() called - discovery is now disabled")
        discoveryEnabled = false
    }

    /**
     * Enable local discovery specifically
     */
    fun enableLocalDiscovery() {
        logger.info("enableLocalDiscovery() called - local discovery is now enabled")
        localDiscoveryEnabled = true
        // Actually start local discovery if it's not already running
        initLocalDiscoveryIfNotYetDone()
    }

    /**
     * Disable local discovery specifically
     */
    fun disableLocalDiscovery() {
        logger.info("disableLocalDiscovery() called - local discovery is now disabled")
        localDiscoveryEnabled = false
    }

    /**
     * Enable global discovery specifically
     */
    fun enableGlobalDiscovery() {
        logger.info("enableGlobalDiscovery() called - global discovery is now enabled")
        globalDiscoveryEnabled = true
        // Actually start global discovery if it's not already running
        doGlobalDiscoveryIfNotYetDone()
    }

    /**
     * Disable global discovery specifically
     */
    fun disableGlobalDiscovery() {
        logger.info("disableGlobalDiscovery() called - global discovery is now disabled")
        globalDiscoveryEnabled = false
    }

    fun newDeviceAddressSupplier(): DeviceAddressSupplier {
        if (isClosed) {
            throw IllegalStateException()
        }

        // Try to start discovery processes if they are enabled
        doGlobalDiscoveryIfNotYetDone()
        initLocalDiscoveryIfNotYetDone()

        return DeviceAddressSupplier(
                peerDevices = configuration.peerIds,
                devicesAddressesManager = devicesAddressesManager
        )
    }

    /**
     * Force a new discovery attempt, used when devices have no known addresses
     */
    fun retryDiscovery() {
        if (isClosed) {
            logger.debug("retryDiscovery() called but handler is closed")
            return
        }
        
        logger.info("retryDiscovery() called - checking devices needing discovery")
        
        // Check if any devices actually need discovery (have no addresses)
        val devicesNeedingDiscovery = configuration.peerIds.filter { deviceId ->
            devicesAddressesManager.getDeviceAddressManager(deviceId).getCurrentDeviceAddresses().isEmpty()
        }
        
        if (devicesNeedingDiscovery.isEmpty()) {
            logger.info("retryDiscovery() skipped - all devices already have addresses")
            return
        }
        
        logger.info("retryDiscovery() proceeding for ${devicesNeedingDiscovery.size} devices without addresses")
        
        // Force a new global discovery attempt by resetting the flag if global discovery is enabled
        if (globalDiscoveryEnabled) {
            shouldLoadFromGlobal = true
            doGlobalDiscoveryIfNotYetDone()
        }
        
        // Also restart local discovery announcements if local discovery is enabled
        if (localDiscoveryEnabled) {
            logger.debug("Sending local discovery announcement")
            localDiscoveryHandler.sendAnnounceMessage()
        }
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
