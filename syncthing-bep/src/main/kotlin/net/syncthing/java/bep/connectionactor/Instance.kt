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

import com.google.protobuf.MessageLite
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.syncthing.java.bep.BlockExchangeProtos
import net.syncthing.java.bep.index.IndexHandler
import net.syncthing.java.core.beans.DeviceAddress
import net.syncthing.java.core.configuration.Configuration
import net.syncthing.java.core.security.KeystoreHandler
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*

@OptIn(kotlinx.coroutines.ObsoleteCoroutinesApi::class)
object ConnectionActor {
    private val logger = LoggerFactory.getLogger("ConnectionActor")

    fun createInstance(
            address: DeviceAddress,
            configuration: Configuration,
            indexHandler: IndexHandler,
            requestHandler: (BlockExchangeProtos.Request) -> Deferred<BlockExchangeProtos.Response>
    ): SendChannel<ConnectionAction> {
        val channel = Channel<ConnectionAction>(Channel.RENDEZVOUS)

        CoroutineScope(Dispatchers.IO).launch {
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                println("💣 Uncaught exception in thread ${thread.name}: ${throwable.message}")
                throwable.printStackTrace()
            }

            OpenConnection.openSocketConnection(address, configuration).use { socket ->
                val inputStream = DataInputStream(socket.inputStream)
                val outputStream = DataOutputStream(socket.outputStream)

                val helloMessage = coroutineScope {
                    async { sendPreAuthenticationMessage(newHelloInstance(configuration), outputStream) }
                    async { receivePreAuthenticationMessage(inputStream) }.await()
                }

                // the hello message exchange should happen before the certificate validation
                KeystoreHandler.assertSocketCertificateValid(socket, address.deviceId)

                // now (after the validation) use the content of the hello message
                processHelloMessage(helloMessage, configuration, address.deviceId)

                // helpers for messages
                val sendPostAuthMessageLock = Mutex()

                suspend fun sendPostAuthMessage(message: MessageLite) = sendPostAuthMessageLock.withLock {
                    PostAuthenticationMessageHandler.sendMessage(outputStream, message, markActivityOnSocket = {})
                }

                suspend fun receivePostAuthMessage(): Pair<BlockExchangeProtos.MessageType, MessageLite> {
                    try {
                        val result = PostAuthenticationMessageHandler.receiveMessage(
                            inputStream = inputStream,
                            markActivityOnSocket = {}
                        )
                        return result
                    } catch (e: Exception) {
                        logger.warn("receivePostAuthMessage failed: ${e.message}")
                        throw e
                    }
                }

                logger.debug("📤 sendPostAuthMessage() sending CLUSTER_CONFIG")
                val clusterConfigPair = try {
                    coroutineScope {
                        launch {
                            sendPostAuthMessage(
                                ClusterConfigHandler.buildClusterConfig(configuration, indexHandler, address.deviceId)
                            )
                        }
                        async {
                            receivePostAuthMessage()
                        }.await()
                    }
                } catch (e: Exception) {
                    logger.error("💥 Exception while receiving post-auth message: ${e.message}")
                    throw e
                }
                logger.debug("📬 Received post-auth message type: ${clusterConfigPair.first}, class: ${clusterConfigPair.second.javaClass.name}")
                val clusterConfig = clusterConfigPair.second

                if (clusterConfig !is BlockExchangeProtos.ClusterConfig) {
                    throw IOException("first message was not a cluster config message")
                }

                val clusterConfigInfo = ClusterConfigHandler.handleReceivedClusterConfig(
                        clusterConfig = clusterConfig,
                        configuration = configuration,
                        otherDeviceId = address.deviceId,
                        indexHandler = indexHandler
                )

                fun hasFolder(folder: String) = clusterConfigInfo.sharedFolderIds.contains(folder)

                val messageListeners = Collections.synchronizedMap(mutableMapOf<Int, CompletableDeferred<BlockExchangeProtos.Response>>())

                try {
                    launch {
                        while (isActive) {
                            val message = receivePostAuthMessage().second

                            when (message) {
                                is BlockExchangeProtos.Response -> {
                                    val listener = messageListeners.remove(message.id)
                                    listener
                                            ?: throw IOException("got response ${message.id} but there is no response listener")
                                    listener.complete(message)
                                }
                                is BlockExchangeProtos.Index -> {
                                    indexHandler.handleIndexMessageReceivedEvent(
                                            folderId = message.folder,
                                            filesList = message.filesList,
                                            clusterConfigInfo = clusterConfigInfo,
                                            peerDeviceId = address.deviceId
                                    )
                                }
                                is BlockExchangeProtos.IndexUpdate -> {
                                    indexHandler.handleIndexMessageReceivedEvent(
                                            folderId = message.folder,
                                            filesList = message.filesList,
                                            clusterConfigInfo = clusterConfigInfo,
                                            peerDeviceId = address.deviceId
                                    )
                                }
                                is BlockExchangeProtos.Request -> {
                                    launch {
                                        val response = requestHandler(message).await()

                                        try {
                                            sendPostAuthMessage(response)
                                        } catch (ex: IOException) {
                                            // the connection was closed in the time between - ignore it
                                        }
                                    }
                                }
                                is BlockExchangeProtos.Ping -> { /* nothing to do */
                                }
                                is BlockExchangeProtos.ClusterConfig -> throw IOException("received cluster config twice")
                                is BlockExchangeProtos.Close -> socket.close()
                                else -> throw IOException("unsupported message type ${message.javaClass}")
                            }
                        }
                    }

                    logger.debug("📁 Local folders in config: ${configuration.folders.map { it.folderId }}")
                    logger.debug("📁 Remote device shares folders: ${clusterConfigInfo.sharedFolderIds}")

                    // send index messages - TODO: Why?
                    for (folder in configuration.folders) {
                        if (hasFolder(folder.folderId)) {
                            sendPostAuthMessage(
                                    BlockExchangeProtos.Index.newBuilder()
                                            .setFolder(folder.folderId)
                                            .build()
                            )
                        }
                    }

                    launch {
                        // send ping all 90 seconds
                        // TODO: only send when there were no messages for 90 seconds

                        while (isActive) {
                            delay(90_000)
                            launch { sendPostAuthMessage(BlockExchangeProtos.Ping.getDefaultInstance()) }
                        }
                    }

                    var nextRequestId = 0

                    channel.consumeEach { action ->
                        when (action) {
                            CloseConnectionAction -> throw InterruptedException()
                            is SendRequestConnectionAction -> {
                                val requestId = nextRequestId++

                                messageListeners[requestId] = action.completableDeferred

                                // async to allow handling the next action faster
                                async {
                                    try {
                                        sendPostAuthMessage(
                                                action.request.toBuilder()
                                                        .setId(requestId)
                                                        .build()
                                        )
                                    } catch (ex: Exception) {
                                        action.completableDeferred.completeExceptionally(ex)
                                    }
                                }
                            }
                            is ConfirmIsConnectedAction -> {
                                action.completableDeferred.complete(clusterConfigInfo)

                                // otherwise, Kotlin would warn that the return
                                // type does not match to the other branches
                                null
                            }
                            is PingAction -> {
                                sendPostAuthMessage(action.ping)
                            }
                            is SendIndexUpdateAction -> {
                                async {
                                    try {
                                        sendPostAuthMessage(action.message)
                                        action.completableDeferred.complete(Unit)
                                    } catch (ex: Exception) {
                                        action.completableDeferred.completeExceptionally(ex)
                                    }
                                }
                            }
                        }.let { /* prevents compiling if one action is not handled */ }
                    }
                } finally {
                    // send close message
                    withContext(NonCancellable) {
                        if (socket.isConnected) {
                            sendPostAuthMessage(BlockExchangeProtos.Close.getDefaultInstance())
                        }
                    }

                    // cancel all pending listeners
                    messageListeners.values.forEach { it.cancel() }
                }
            }
        }.invokeOnCompletion { ex ->
            if (ex != null) {
                channel.close(ex)
            } else {
                channel.close()
            }
        }

        return channel
    }
}
