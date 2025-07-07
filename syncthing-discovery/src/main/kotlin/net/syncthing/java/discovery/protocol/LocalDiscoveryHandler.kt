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

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.configuration.Configuration
import net.syncthing.java.core.exception.ExceptionReport
import net.syncthing.java.core.exception.reportExceptions
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.IOException

@OptIn(kotlinx.coroutines.ObsoleteCoroutinesApi::class)
internal class LocalDiscoveryHandler(
        private val configuration: Configuration,
        private val exceptionReportHandler: (ExceptionReport) -> Unit,
        private val onMessageReceivedListener: (LocalDiscoveryMessage) -> Unit,
        private val onMessageFromUnknownDeviceListener: (DeviceId) -> Unit = {}
) : Closeable {
    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    fun sendAnnounceMessage() {
        scope.launch {
            LocalDiscoveryUtil.sendAnnounceMessage(
                    ownDeviceId = configuration.localDeviceId,
                    instanceId = configuration.instanceId
            )
        }.reportExceptions("LocalDiscoveryHandler.sendAnnounceMessage", exceptionReportHandler)
    }

    fun startListener() {
        scope.launch {
            try {
                LocalDiscoveryUtil.listenForAnnounceMessages().consumeEach { message ->
                    if (message.deviceId == configuration.localDeviceId) {
                        // ignore announcement received from ourselves.
                    } else if (!configuration.peerIds.contains(message.deviceId)) {
                        logger.trace("Received local announcement from {}, which is not a peer, therefore ignoring.", message.deviceId)

                        onMessageFromUnknownDeviceListener(message.deviceId)
                    } else {
                        logger.debug("Received local announcement from device ID: {}.", message.deviceId)

                        onMessageReceivedListener(message)
                    }
                }
            } catch (ex: IOException) {
                logger.warn("Failed to listen for announcement messages", ex)
            }
        }.reportExceptions("LocalDiscoveryHandler.startListener", exceptionReportHandler)
    }

    override fun close() {
        job.cancel()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LocalDiscoveryHandler::class.java)
    }
}
