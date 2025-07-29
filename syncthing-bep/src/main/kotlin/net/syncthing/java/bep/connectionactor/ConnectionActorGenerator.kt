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

import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.*
import net.syncthing.java.bep.BlockExchangeProtos
import net.syncthing.java.bep.index.IndexHandler
import net.syncthing.java.core.beans.DeviceAddress
import net.syncthing.java.core.configuration.Configuration
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import java.io.IOException

data class Connection (
        val actor: SendChannel<ConnectionAction>,
        val clusterConfigInfo: ClusterConfigInfo
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.ObsoleteCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
object ConnectionActorGenerator {
    private val closed = Channel<ConnectionAction>().apply { cancel() }
    private val logger = LoggerFactory.getLogger(ConnectionActorGenerator::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Check if a channel is open for sending.
     * We check both that it's not the closed sentinel and that it's actually open for sending.
     */
    private fun isChannelOpen(channel: SendChannel<ConnectionAction>): Boolean {
        return channel != closed && !channel.isClosedForSend
    }

    private fun deviceAddressesGenerator(deviceAddress: ReceiveChannel<DeviceAddress>) = scope.produce<List<DeviceAddress>> (capacity = Channel.CONFLATED) {
        val addresses = mutableMapOf<String, DeviceAddress>()

        deviceAddress.consumeEach { address ->
            val isNew = addresses[address.address] == null

            addresses[address.address] = address

            if (isNew) {
                send(
                        addresses.values.sortedBy { it.score }
                )
            }
        }
    }

    private fun <T> waitForFirstValue(source: ReceiveChannel<T>, time: Long) = scope.produce<T> {
        source.consume {
            val firstValue = source.receive()
            var lastValue = firstValue

            try {
                withTimeout(time) {
                    while (true) {
                        lastValue = source.receive()
                    }
                }
            } catch (ex: TimeoutCancellationException) {
                // this is expected here
            }

            send(lastValue)

            // other values without delay
            for (value in source) {
                send(value)
            }
        }
    }

    fun generateConnectionActors(
            deviceAddress: ReceiveChannel<DeviceAddress>,
            configuration: Configuration,
            indexHandler: IndexHandler,
            requestHandler: (BlockExchangeProtos.Request) -> Deferred<BlockExchangeProtos.Response>
    ) = generateConnectionActorsFromDeviceAddressList(
            deviceAddressSource = waitForFirstValue(
                    source = deviceAddressesGenerator(deviceAddress),
                    time = 1000
            ),
            configuration = configuration,
            indexHandler = indexHandler,
            requestHandler = requestHandler
    )

    fun generateConnectionActorsFromDeviceAddressList(
            deviceAddressSource: ReceiveChannel<List<DeviceAddress>>,
            configuration: Configuration,
            indexHandler: IndexHandler,
            requestHandler: (BlockExchangeProtos.Request) -> Deferred<BlockExchangeProtos.Response>
    ) = scope.produce<Pair<Connection, ConnectionInfo>> {
        var currentActor: SendChannel<ConnectionAction> = closed
        var currentClusterConfig = ClusterConfigInfo.dummy
        var currentDeviceAddress: DeviceAddress? = null
        var currentStatus = ConnectionInfo.empty

        suspend fun dispatchStatus() {
            send(Connection(currentActor, currentClusterConfig) to currentStatus)
        }

        suspend fun closeCurrent() {
            if (currentActor != closed) {
                currentActor.close()
                currentActor = closed
                currentClusterConfig = ClusterConfigInfo.dummy

                if (currentStatus.status != ConnectionStatus.Disconnected) {
                    currentStatus = currentStatus.copy(status = ConnectionStatus.Disconnected)
                }

                dispatchStatus()
            }
        }

        suspend fun dispatchConnection(
                connection: SendChannel<ConnectionAction>,
                clusterConfig: ClusterConfigInfo,
                deviceAddress: DeviceAddress
        ) {
            currentActor = connection
            currentDeviceAddress = deviceAddress
            currentClusterConfig = clusterConfig

            dispatchStatus()
        }

        suspend fun tryConnectingToAddressHandleBaseErrors(deviceAddress: DeviceAddress): Pair<SendChannel<ConnectionAction>, ClusterConfigInfo>? = try {
            val newActor = ConnectionActor.createInstance(deviceAddress, configuration, indexHandler, requestHandler)
            
            // logger.trace("Created connection actor for $deviceAddress, waiting for connection setup")
            
            // Use a timeout for connection setup to avoid hanging indefinitely
            val clusterConfig = try {
                withTimeout(30000) { // 30 second timeout for connection setup
                    ConnectionActorUtil.waitUntilConnected(newActor)
                }
            } catch (e: TimeoutCancellationException) {
                logger.trace("Connection setup timed out for $deviceAddress")
                newActor.close()
                throw IOException("Connection setup timeout for $deviceAddress")
            } catch (e: Exception) {
                logger.warn("Connection setup failed for $deviceAddress: ${e.message}")
                newActor.close()
                throw e
            }
            
            // logger.trace("Connection setup succeeded for $deviceAddress")
            newActor to clusterConfig
        } catch (ex: Exception) {
            // Log "Connection reset" at debug level since it's expected when remote device hasn't accepted connection yet
            if (ex.message?.contains("Connection reset") == true) {
                logger.trace("Connection reset detected - this is expected when remote device hasn't accepted connection yet")
            }

            when (ex) {
                is IOException -> {/* expected -> ignore */}
                is InterruptedException -> {/* expected -> ignore */}
                is TimeoutCancellationException -> {/* expected -> ignore */}
                else -> throw ex
            }

            null
        }

        suspend fun tryConnectingToAddress(deviceAddress: DeviceAddress): Boolean {
            closeCurrent()

            suspend fun handleCancel() {
                currentStatus = currentStatus.copy(
                        status = ConnectionStatus.Disconnected
                )
                dispatchStatus()
                // logger.trace("Connection attempt to $deviceAddress failed, status set to Disconnected")
            }

            currentStatus = currentStatus.copy(
                    status = ConnectionStatus.Connecting,
                    currentAddress = deviceAddress
            )
            dispatchStatus()
            // logger.trace("Attempting to connect to $deviceAddress")

            var connection = tryConnectingToAddressHandleBaseErrors(deviceAddress) ?: return run {
                // logger.trace("Connection to $deviceAddress failed, will retry later")
                handleCancel()
                false
            }

            if (connection.second.newSharedFolders.isNotEmpty()) {
                logger.trace("Connected to device {} with new folders --> Reconnect.", deviceAddress)
                // reconnect to send new cluster config
                connection.first.close()
                connection = tryConnectingToAddressHandleBaseErrors(deviceAddress) ?: return run {
                    // logger.trace("Reconnection to $deviceAddress failed, will retry later")
                    handleCancel()
                    false
                }
            }

            logger.trace("Connected to device {}.", deviceAddress.deviceId.deviceId)

            currentStatus = currentStatus.copy(
                    status = ConnectionStatus.Connected,
                    currentAddress = deviceAddress
            )
            dispatchConnection(connection.first, connection.second, deviceAddress)

            return true
        }

        fun isConnected() = (currentActor != closed && isChannelOpen(currentActor)) /* .also { connected ->
            logger.trace("isConnected() = $connected, currentActor open = ${isChannelOpen(currentActor)}")
        } */

        invokeOnClose {
            currentActor.close()
        }

        val reconnectTicker = ticker(delayMillis = 30 * 1000, initialDelayMillis = 0)

        deviceAddressSource.consume {
            while (true) {
                run {
                    // get the new list version if there is any
                    val newDeviceAddressList = deviceAddressSource.tryReceive().getOrNull()

                    if (newDeviceAddressList != null) {
                        currentStatus = currentStatus.copy(addresses = newDeviceAddressList)
                        dispatchStatus()
                    }
                }

                if (isConnected()) {
                    val deviceAddressList = currentStatus.addresses

                    if (deviceAddressList.isNotEmpty()) {
                        if (reconnectTicker.tryReceive().getOrNull() != null) {
                            if (currentDeviceAddress != deviceAddressList.first()) {
                                val oldDeviceAddress = currentDeviceAddress!!

                                if (!tryConnectingToAddress(deviceAddressList.first())) {
                                    tryConnectingToAddress(oldDeviceAddress)
                                }
                            }
                        }
                    } else {
                        closeCurrent()
                    }

                    delay(500)  // don't take too much CPU
                } else /* is not connected */ {
                    if (currentStatus.status == ConnectionStatus.Connected) {
                        logger.trace("Status was Connected but isConnected() returned false, setting to Disconnected")
                        currentStatus = currentStatus.copy(status = ConnectionStatus.Disconnected)
                        dispatchStatus()
                    }

                    val deviceAddressList = currentStatus.addresses
                    // logger.trace("Not connected (status: ${currentStatus.status}), trying to connect to ${deviceAddressList.size} addresses")

                    if (deviceAddressList.isEmpty()) {
                        logger.trace("No addresses available, waiting for discovery")
                    } else {
                        // try all addresses
                        var connectionSuccessful = false
                        for (address in deviceAddressList) {
                            // logger.trace("Attempting to connect to address: $address")
                            try {
                                if (tryConnectingToAddress(address)) {
                                    // logger.trace("Successfully connected to address: $address")
                                    connectionSuccessful = true
                                    break
                                } else {
                                    // logger.trace("Failed to connect to address: $address")
                                }
                            } catch (e: Exception) {
                                logger.warn("Exception while connecting to address $address: ${e.message}")
                            }
                        }
                        
                        /*
                        if (!connectionSuccessful) {
                            logger.trace("All connection attempts failed, will retry after delay")
                        }
                        */
                    }

                    // reset countdown before trying other connection if it would be time now
                    // this does not reset if it has not counted down the whole time yet
                    reconnectTicker.tryReceive().getOrNull()

                    // wait for new device address list but not more than 15 seconds before the next iteration
                    val newDeviceAddressList = withTimeoutOrNull(15 * 1000) {
                        deviceAddressSource.receive()
                    }

                    if (newDeviceAddressList != null) {
                        logger.trace("Received new device address list with ${newDeviceAddressList.size} addresses")
                        currentStatus = currentStatus.copy(addresses = newDeviceAddressList)
                        dispatchStatus()
                    }
                }
            }
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
