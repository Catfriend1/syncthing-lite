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
    private var discoveryEnabled = false // Only start discovery when explicitly enabled
    private var localDiscoveryEnabled = false // Control local discovery separately
    private var globalDiscoveryEnabled = false // Control global discovery separately
    private var lastGlobalDiscoveryTime = 0L
    private var globalDiscoveryRetryInterval = 30_000L // Start with 30 seconds
    private val maxRetryInterval = 300_000L // Max 5 minutes

    private fun doGlobalDiscoveryIfNotYetDone() {
        // Only proceed if discovery is enabled AND global discovery is specifically enabled
        if (!discoveryEnabled || !globalDiscoveryEnabled) {
            logger.trace("doGlobalDiscoveryIfNotYetDone() skipped - discovery enabled: $discoveryEnabled, global enabled: $globalDiscoveryEnabled")
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val timeSinceLastDiscovery = currentTime - lastGlobalDiscoveryTime
        
        logger.debug("doGlobalDiscoveryIfNotYetDone() called - shouldLoadFromGlobal: $shouldLoadFromGlobal, timeSinceLastDiscovery: ${timeSinceLastDiscovery}ms, retryInterval: ${globalDiscoveryRetryInterval}ms")
        
        if (shouldLoadFromGlobal || timeSinceLastDiscovery > globalDiscoveryRetryInterval) {
            logger.info("Starting global discovery - shouldLoadFromGlobal: $shouldLoadFromGlobal, timeSinceLastDiscovery: ${timeSinceLastDiscovery}ms")
            shouldLoadFromGlobal = false
            lastGlobalDiscoveryTime = currentTime
            
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    logger.info("Calling globalDiscoveryHandler.query() for ${configuration.peerIds.size} devices")
                    val deviceAddresses = globalDiscoveryHandler.query(configuration.peerIds)
                    logger.info("Global discovery completed, received ${deviceAddresses.size} addresses")
                    processDeviceAddressBg(deviceAddresses)
                    
                    // If no addresses were found for any device, schedule a retry with exponential backoff
                    val hasAddressesForAllDevices = configuration.peerIds.all { deviceId ->
                        devicesAddressesManager.getDeviceAddressManager(deviceId).getCurrentDeviceAddresses().isNotEmpty()
                    }
                    
                    if (!hasAddressesForAllDevices) {
                        // Increase retry interval with exponential backoff, but cap at max
                        globalDiscoveryRetryInterval = minOf(globalDiscoveryRetryInterval * 2, maxRetryInterval)
                        logger.info("Global discovery found no addresses for some devices, will retry in ${globalDiscoveryRetryInterval}ms")
                    } else {
                        // Reset retry interval if successful
                        globalDiscoveryRetryInterval = 30_000L
                        logger.info("Global discovery successful for all devices, reset retry interval to 30s")
                    }
                } catch (e: Exception) {
                    logger.error("Global discovery failed with exception", e)
                }
            }
        } else {
            logger.debug("Global discovery skipped - waiting for retry interval (${globalDiscoveryRetryInterval - timeSinceLastDiscovery}ms remaining)")
        }
    }

    private fun initLocalDiscoveryIfNotYetDone() {
        // Only proceed if discovery is enabled AND local discovery is specifically enabled
        if (!discoveryEnabled || !localDiscoveryEnabled) {
            logger.trace("initLocalDiscoveryIfNotYetDone() skipped - discovery enabled: $discoveryEnabled, local enabled: $localDiscoveryEnabled")
            return
        }
        
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
        
        if (!discoveryEnabled) {
            logger.debug("retryDiscovery() called but discovery is not enabled")
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
        
        // Force a new global discovery attempt by resetting the flag
        // This bypasses the retry interval when explicitly called
        shouldLoadFromGlobal = true
        doGlobalDiscoveryIfNotYetDone()
        
        // Also restart local discovery announcements
        logger.debug("Sending local discovery announcement")
        localDiscoveryHandler.sendAnnounceMessage()
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
