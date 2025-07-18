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
package net.syncthing.java.discovery.protocol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.syncthing.java.core.beans.DeviceAddress
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.configuration.Configuration
import net.syncthing.java.core.configuration.DiscoveryServer
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import java.io.IOException

internal class GlobalDiscoveryHandler(private val configuration: Configuration) {

    @Deprecated(message = "coroutine version should be used instead of callback")
    fun query(deviceId: DeviceId, callback: (List<DeviceAddress>) -> Unit) = CoroutineScope(Dispatchers.IO).launch {
        try {
            callback(query(deviceId))
        } catch (ex: Exception) {
            callback(emptyList())
        }
    }

    suspend fun query(deviceIds: Collection<DeviceId>): List<DeviceAddress> {
        logger.info("GlobalDiscoveryHandler.query() called for ${deviceIds.size} devices: ${deviceIds.joinToString { it.deviceId.substring(0, 8) }}")
        val discoveryServers = getLookupServers()
        logger.info("Using ${discoveryServers.size} discovery servers for lookup")

        return coroutineScope {
            deviceIds
                    .distinct()
                    .map { deviceId ->
                        async {
                            logger.debug("Starting query for device ${deviceId.deviceId.substring(0, 8)}")
                            val addresses = queryAnnounceServers(
                                    servers = discoveryServers,
                                    deviceId = deviceId
                            )
                            logger.debug("Query completed for device ${deviceId.deviceId.substring(0, 8)}, found ${addresses.size} addresses")
                            addresses
                        }
                    }
                    .map { it.await() }
                    .flatten()
                    .also { addresses ->
                        logger.info("GlobalDiscoveryHandler.query() completed, total addresses found: ${addresses.size}")
                    }
        }
    }

    suspend fun query(deviceId: DeviceId) = queryAnnounceServers(
            servers = getLookupServers(),
            deviceId = deviceId
    )

    fun getLookupServers() = configuration.discoveryServers.filter { it.useForLookup }.also { servers ->
        logger.debug("getLookupServers() found ${servers.size} servers: ${servers.joinToString { it.hostname }}")
    }

    suspend fun queryAnnounceServers(servers: List<DiscoveryServer>, deviceId: DeviceId) = coroutineScope {
        logger.debug("queryAnnounceServers() called for device ${deviceId.deviceId.substring(0, 8)} with ${servers.size} servers")
        servers
                .map { server ->
                    async {
                        try {
                            logger.debug("Querying server ${server.hostname} for device ${deviceId.deviceId.substring(0, 8)}")
                            val addresses = queryAnnounceServer(server, deviceId)
                            logger.debug("Server ${server.hostname} returned ${addresses.size} addresses for device ${deviceId.deviceId.substring(0, 8)}")
                            addresses
                        } catch (ex: Exception) {
                            logger.warn("Failed to query $server for $deviceId", ex)

                            when (ex) {
                                is IOException -> { /* ignore */ }
                                is DeviceNotFoundException -> { /* ignore */ }
                                is TooManyRequestsException -> { /* ignore */ }
                                else -> throw ex
                            }

                            emptyList<DeviceAddress>()
                        }
                    }
                }
                .map { it.await() }
                .flatten()
                .also { addresses ->
                    logger.debug("queryAnnounceServers() completed for device ${deviceId.deviceId.substring(0, 8)}, total addresses: ${addresses.size}")
                }
        // .distinct() is not required because the device addresses contain the used discovery server
    }

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalDiscoveryHandler::class.java)
        suspend fun queryAnnounceServer(server: DiscoveryServer, deviceId: DeviceId) =
                GlobalDiscoveryUtil
                        .queryAnnounceServer(
                                server = server.hostname,
                                requestedDeviceId = deviceId,
                                serverDeviceId = server.deviceId
                        )
                        .addresses.map { DeviceAddress(deviceId.deviceId, it) }
    }
}
