/* 
 * Copyright (C) 2016 Davide Imbriaco
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
package net.syncthing.java.client.protocol.rp

import net.syncthing.java.client.protocol.rp.beans.SessionInvitation
import net.syncthing.java.core.beans.DeviceAddress
import net.syncthing.java.core.beans.DeviceAddress.AddressType
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.configuration.Configuration
import net.syncthing.java.core.interfaces.RelayConnection
import net.syncthing.java.core.security.KeystoreHandler
import net.syncthing.java.core.utils.NetworkUtils
import org.apache.commons.io.IOUtils
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import org.bouncycastle.util.encoders.Hex
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

class RelayClient(configuration: Configuration) {
    private val keystoreHandler: KeystoreHandler = KeystoreHandler.Loader().loadKeystore(configuration)

    @Throws(IOException::class, KeystoreHandler.CryptoException::class)
    fun openRelayConnection(address: DeviceAddress): RelayConnection {
        assert(address.type == AddressType.RELAY)
        val sessionInvitation = getSessionInvitation(address.getSocketAddress(), address.deviceId)
        return openConnectionSessionMode(sessionInvitation)
    }

    @Throws(IOException::class)
    private fun openConnectionSessionMode(sessionInvitation: SessionInvitation): RelayConnection {
        logger.debug("Connecting to relay (Session Mode): Address {}, Port {}.", sessionInvitation.address, sessionInvitation.port)
        val socket = Socket(sessionInvitation.address, sessionInvitation.port)
        val inputStream = RelayDataInputStream(socket.getInputStream())
        val outputStream = RelayDataOutputStream(socket.getOutputStream())
        run {
            logger.debug("Sending join session request with session key: {}.", sessionInvitation.key)
            val key = Hex.decode(sessionInvitation.key)
            val lengthOfKey = key.size
            outputStream.writeHeader(JOIN_SESSION_REQUEST, 4 + lengthOfKey)
            outputStream.writeInt(lengthOfKey)
            outputStream.write(key)
            outputStream.flush()
        }
        run {
            logger.debug("Reading relay response.")
            val messageReader = inputStream.readMessage()
            NetworkUtils.assertProtocol(messageReader.type == RESPONSE)
            val response = messageReader.readResponse()
            logger.debug("Response from relay: {}.", response)
            NetworkUtils.assertProtocol(response.code == ResponseSuccess, {"response code = ${response.code} (${response.message}) expected $ResponseSuccess"})
            logger.debug("Relay connection ready.")
        }
        return object : RelayConnection {
            override fun getSocket(): Socket {
                return socket
            }

            override fun isServerSocket(): Boolean {
                return sessionInvitation.isServerSocket
            }

        }
    }

    @Throws(IOException::class, KeystoreHandler.CryptoException::class)
    fun getSessionInvitation(relaySocketAddress: InetSocketAddress, deviceId: DeviceId): SessionInvitation {
        logger.debug("Connecting to relay (temporary protocol mode) at address: {}.", relaySocketAddress)
        keystoreHandler.createSocket(relaySocketAddress).use { socket ->
            RelayDataInputStream(socket.getInputStream()).use { `in` ->
                RelayDataOutputStream(socket.getOutputStream()).use { out ->
                    run {
                        logger.debug("Sending connect request for device ID: {}.", deviceId)
                        val deviceIdData = deviceId.toHashData()
                        val lengthOfId = deviceIdData.size
                        out.writeHeader(CONNECT_REQUEST, 4 + lengthOfId)
                        out.writeInt(lengthOfId)
                        out.write(deviceIdData)
                        out.flush()
                    }

                    run {
                        logger.debug("Receiving session invitation.")
                        val messageReader = `in`.readMessage()
                        logger.debug("Received message: {}.", messageReader.dumpMessageForDebug())
                        if (messageReader.type == RESPONSE) {
                            val response = messageReader.readResponse()
                            throw IOException(response.message)
                        }
                        NetworkUtils.assertProtocol(messageReader.type == SESSION_INVITATION, {"message type mismatch, expected $SESSION_INVITATION, got ${messageReader.type}"})
                        val from = DeviceId.fromHashData(messageReader.readLengthAndData()).deviceId
                        val key = Hex.toHexString(messageReader.readLengthAndData())
                        val address = messageReader.readLengthAndData()
                        val invitationAddress =  if (address.isEmpty()) {
                            socket.inetAddress
                        } else {
                            val inetAddress = InetAddress.getByAddress(address)
                            if (inetAddress == InetAddress.getByName("0.0.0.0")) {
                                socket.inetAddress
                            } else {
                                inetAddress
                            }
                        }
                        val zero = messageReader.buffer.short.toInt()
                        NetworkUtils.assertProtocol(zero == 0, {"expected 0, found $zero"})
                        val port = messageReader.buffer.short.toInt()
                        NetworkUtils.assertProtocol(port > 0, {"got invalid port value = $port"})
                        val serverSocket = messageReader.buffer.int and 1
                        val isServerSocket = serverSocket == 1
                        logger.debug("Closing connection (temporary protocol mode).")
                        return SessionInvitation(from, key, invitationAddress, port, isServerSocket)
                    }
                }
            }
        }
    }

    private class RelayDataOutputStream(out: OutputStream) : DataOutputStream(out) {

        @Throws(IOException::class)
        fun writeHeader(type: Int, length: Int) {
            writeInt(MAGIC)
            writeInt(type)
            writeInt(length)
        }

    }

    private class RelayDataInputStream(`in`: InputStream) : DataInputStream(`in`) {

        @Throws(IOException::class)
        fun readMessage(): MessageReader {
            val magic = readInt()
            NetworkUtils.assertProtocol(magic == MAGIC, {"magic mismatch, got = $magic, expected = $MAGIC"})
            val type = readInt()
            val length = readInt()
            NetworkUtils.assertProtocol(length >= 0)
            val payload = ByteBuffer.allocate(length)
            IOUtils.readFully(this, payload.array())
            return MessageReader(type, payload)
        }
    }

    private class Response(val code: Int, val message: String) {

        override fun toString(): String {
            return "Response{code=$code, message=$message}"
        }

    }

    private class MessageReader(val type: Int, val buffer: ByteBuffer) {

        fun readLengthAndData(): ByteArray {
            val length = buffer.int
            NetworkUtils.assertProtocol(length >= 0)
            val data = ByteArray(length)
            buffer.get(data)
            return data
        }

        fun readResponse(): Response {
            val code = buffer.int
            val messageLength = buffer.int
            val message = ByteArray(messageLength)
            buffer.get(message)
            return Response(code, String(message))
        }

        fun cloneReader(): MessageReader {
            return MessageReader(type, ByteBuffer.wrap(buffer.array()))
        }

        fun dumpMessageForDebug(): String {
            return if (type == RESPONSE) {
                "Response(code=${cloneReader().readResponse().code}, message=${cloneReader().readResponse().message})"
            } else {
                "Message(type=$type, size=${buffer.capacity()})"
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RelayClient::class.java)
        private const val MAGIC = -0x618643c0
        private const val JOIN_SESSION_REQUEST = 3
        private const val RESPONSE = 4
        private const val CONNECT_REQUEST = 5
        private const val SESSION_INVITATION = 6
        private const val ResponseSuccess = 0
        private const val ResponseNotFound = 1
        private const val ResponseAlreadyConnected = 2
    }
}
