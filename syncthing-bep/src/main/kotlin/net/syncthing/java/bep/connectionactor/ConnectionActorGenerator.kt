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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.ObsoleteCoroutinesApi::class)
object ConnectionActorGenerator {
    private val closed = Channel<ConnectionAction>().apply { cancel() }
    private val logger = LoggerFactory.getLogger(ConnectionActorGenerator::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
            val clusterConfig = ConnectionActorUtil.waitUntilConnected(newActor)

            newActor to clusterConfig
        } catch (ex: Exception) {
            // Log "Connection reset" at debug level since it's expected when remote device hasn't accepted connection yet
            if (ex.message?.contains("Connection reset") == true) {
                logger.debug("Connection reset detected - this is expected when remote device hasn't accepted connection yet")
            } else {
                logger.warn("failed to connect to $deviceAddress", ex)
            }

            when (ex) {
                is IOException -> {/* expected -> ignore */}
                is InterruptedException -> {/* expected -> ignore */}
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
                logger.debug("ConnectionActorGenerator: Connection attempt to $deviceAddress failed, status set to Disconnected")
            }

            currentStatus = currentStatus.copy(
                    status = ConnectionStatus.Connecting,
                    currentAddress = deviceAddress
            )
            dispatchStatus()
            logger.debug("ConnectionActorGenerator: Attempting to connect to $deviceAddress")

            var connection = tryConnectingToAddressHandleBaseErrors(deviceAddress) ?: return run {
                logger.debug("ConnectionActorGenerator: Connection to $deviceAddress failed, will retry later")
                handleCancel()
                false
            }

            if (connection.second.newSharedFolders.isNotEmpty()) {
                logger.debug("Connected to device {} with new folders --> Reconnect.", deviceAddress)
                // reconnect to send new cluster config
                connection.first.close()
                connection = tryConnectingToAddressHandleBaseErrors(deviceAddress) ?: return run {
                    logger.debug("ConnectionActorGenerator: Reconnection to $deviceAddress failed, will retry later")
                    handleCancel()
                    false
                }
            }

            logger.debug("Connected to device {}.", deviceAddress)

            currentStatus = currentStatus.copy(
                    status = ConnectionStatus.Connected,
                    currentAddress = deviceAddress
            )
            dispatchConnection(connection.first, connection.second, deviceAddress)

            // Monitor the connection actor to detect when it terminates
            scope.launch {
                try {
                    // Wait for the connection actor to close
                    connection.first.invokeOnClose { cause ->
                        logger.debug("ConnectionActorGenerator: Connection actor closed: $cause")
                        scope.launch {
                            // Set status to disconnected when connection actor closes
                            if (currentActor == connection.first) {
                                currentStatus = currentStatus.copy(status = ConnectionStatus.Disconnected)
                                currentActor = closed
                                dispatchStatus()
                                logger.debug("ConnectionActorGenerator: Connection lost, status set to Disconnected")
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("ConnectionActorGenerator: Error monitoring connection: ${e.message}")
                }
            }

            return true
        }

        fun isConnected() = (currentActor != closed && !currentActor.isClosedForSend).also { connected ->
            logger.debug("ConnectionActorGenerator: isConnected() = $connected, currentActor.isClosedForSend = ${currentActor.isClosedForSend}")
        }

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
                        logger.debug("ConnectionActorGenerator: Status was Connected but isConnected() returned false, setting to Disconnected")
                        currentStatus = currentStatus.copy(status = ConnectionStatus.Disconnected)
                        dispatchStatus()
                    }

                    val deviceAddressList = currentStatus.addresses
                    logger.debug("ConnectionActorGenerator: Not connected (status: ${currentStatus.status}), trying to connect to ${deviceAddressList.size} addresses")

                    if (deviceAddressList.isEmpty()) {
                        logger.debug("ConnectionActorGenerator: No addresses available, waiting for discovery")
                    } else {
                        // try all addresses
                        for (address in deviceAddressList) {
                            logger.debug("ConnectionActorGenerator: Attempting to connect to address: $address")
                            try {
                                if (tryConnectingToAddress(address)) {
                                    logger.debug("ConnectionActorGenerator: Successfully connected to address: $address")
                                    break
                                } else {
                                    logger.debug("ConnectionActorGenerator: Failed to connect to address: $address")
                                }
                            } catch (e: Exception) {
                                logger.debug("ConnectionActorGenerator: Exception while connecting to address $address: ${e.message}")
                            }
                        }
                    }

                    // reset countdown before trying other connection if it would be time now
                    // this does not reset if it has not counted down the whole time yet
                    reconnectTicker.tryReceive().getOrNull()

                    // Use standard 15 second retry interval as requested
                    val retryTimeout = 15L * 1000 // 15 seconds for all failures

                    logger.debug("ConnectionActorGenerator: Waiting for retry (timeout: ${retryTimeout}ms)")

                    // wait for new device address list but not more than the retry timeout before the next iteration
                    val newDeviceAddressList = withTimeoutOrNull(retryTimeout) {
                        deviceAddressSource.receive()
                    }

                    if (newDeviceAddressList != null) {
                        logger.debug("ConnectionActorGenerator: Received new device address list with ${newDeviceAddressList.size} addresses")
                        currentStatus = currentStatus.copy(addresses = newDeviceAddressList)
                        dispatchStatus()
                    } else {
                        logger.debug("ConnectionActorGenerator: Retry timeout reached, will try again in next iteration")
                    }
                }
            }
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
