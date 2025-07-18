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
import net.jpountz.lz4.LZ4Factory
import net.syncthing.java.bep.BlockExchangeProtos
import net.syncthing.java.core.utils.NetworkUtils
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer

object PostAuthenticationMessageHandler {
    private val logger = LoggerFactory.getLogger(PostAuthenticationMessageHandler::class.java)
    
    // private var messageCounter = 0

    fun sendMessage(
            outputStream: DataOutputStream,
            message: MessageLite,
            markActivityOnSocket: () -> Unit
    ) {
        val messageTypeInfo = MessageTypes.messageTypesByJavaClass[message.javaClass]!!
        val header = BlockExchangeProtos.Header.newBuilder()
                .setCompression(BlockExchangeProtos.MessageCompression.NONE)
                .setType(messageTypeInfo.protoMessageType)
                .build()
        val headerData = header.toByteArray()
        val messageData = message.toByteArray() //TODO support compression

        logger.trace("Sending message type: {} {}.", header.type, MessageTypes.getIdForMessage(message))
        markActivityOnSocket()

        outputStream.apply {
            writeShort(headerData.size)
            write(headerData)
            writeInt(messageData.size)
            write(messageData)
            flush()
        }

        markActivityOnSocket()
    }

    fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02x".format(it) }

    fun receiveMessage(
            inputStream: DataInputStream,
            markActivityOnSocket: () -> Unit
    ): Pair<BlockExchangeProtos.MessageType, MessageLite> {
        val headerBytes = readHeader(
            inputStream = inputStream,
            markActivityOnSocket = markActivityOnSocket
        )

        // logger.debug("🔹 Raw header bytes: ${headerBytes.toHexString()}")
        val header: BlockExchangeProtos.Header = if (headerBytes.isEmpty()) {
            logger.warn("📭 Header bytes were empty — using default Header")
            BlockExchangeProtos.Header.getDefaultInstance()
        } else {
            BlockExchangeProtos.Header.parseFrom(headerBytes)
        }
        // logger.debug("📦 Message compression: ${header.compression}, type: ${header.type}")

        var messageBuffer = readMessage(
                inputStream = inputStream,
                retryReadingLength = true,
                markActivityOnSocket = markActivityOnSocket
        )

        // logger.debug("🔸 Raw message buffer (${messageBuffer.size} bytes): ${messageBuffer.take(64).toByteArray().toHexString()}")

        if (header.compression == BlockExchangeProtos.MessageCompression.LZ4) {
            val uncompressedLength = ByteBuffer.wrap(messageBuffer).int
            // logger.debug("💨 LZ4 compression detected. Uncompressed length: $uncompressedLength")
            messageBuffer = LZ4Factory.fastestInstance().fastDecompressor()
                .decompress(messageBuffer, 4, uncompressedLength)
            // logger.debug("✅ Successfully decompressed. First 64 bytes: ${messageBuffer.take(64).toByteArray().toHexString()}")
        }

        val messageTypeInfo = MessageTypes.messageTypesByProtoMessageType[header.type]
        NetworkUtils.assertProtocol(messageTypeInfo != null) {"unsupported message type = ${header.type}"}

        // logger.debug("📨 Received #${++messageCounter}: ${header.type} (${messageBuffer.size} bytes)")

        try {
            val parsed = messageTypeInfo!!.parseFrom(messageBuffer)
            logger.debug("✅ Successfully parsed message of type ${header.type}")
            return header.type to parsed
        } catch (e: Exception) {
            logger.error("🔥 Parsing exception for ${header.type}: ${e.message}", e)
            throw when (e) {
                is IllegalAccessException, is IllegalArgumentException,
                is InvocationTargetException, is NoSuchMethodException,
                is SecurityException -> IOException(e)
                else -> e
            }
        }
    }

    private fun readHeader(
            inputStream: DataInputStream,
            markActivityOnSocket: () -> Unit
    ): ByteArray {
        val headerLength = inputStream.readShort().toInt() and 0xffff // Ensure unsigned
        // logger.debug("🔍 [readHeader] Raw headerLength read: $headerLength")

        if (headerLength == 0) {
            logger.debug("📭 headerLength == 0, message may be keepalive or without header")
            return ByteArray(0)
        }

        markActivityOnSocket()

        NetworkUtils.assertProtocol(headerLength > 0) {
            "invalid header length, must be > 0, got $headerLength"
        }

        return ByteArray(headerLength).also {
            inputStream.readFully(it)
        }
    }

    private fun readMessage(
            inputStream: DataInputStream,
            markActivityOnSocket: () -> Unit,
            retryReadingLength: Boolean
    ): ByteArray {
        var messageLength = inputStream.readInt()

        // logger.debug("📏 Raw messageLength read: $messageLength")

        if (messageLength == 0) {
            logger.warn("⚠️ Message length is zero — skipping readFully, maybe keepalive?")
            return ByteArray(0)
        }

        // TODO: what is this good for?
        if (retryReadingLength) {
            while (messageLength == 0) {
                logger.warn("Received message of length zero (0), expecting 'bep message header length' (int >0), ignoring (keepalive?).")
                messageLength = inputStream.readInt()
            }
        }

        NetworkUtils.assertProtocol(messageLength >= 0) {"invalid length, must be >= 0, got $messageLength"}

        // logger.debug("📥 Reading full messageBuffer ($messageLength bytes)...")
        val messageBuffer = ByteArray(messageLength)
        var bytesRead = 0
        while (bytesRead < messageLength) {
            val result = inputStream.read(messageBuffer, bytesRead, messageLength - bytesRead)
            if (result == -1) {
                logger.warn("🛑 Stream ended unexpectedly after $bytesRead bytes (expected $messageLength)")
                break
            }
            bytesRead += result
            // logger.debug("📶 Read $result bytes (total: $bytesRead/$messageLength)")
        }
        // logger.debug("📥 Successfully read messageBuffer")
        markActivityOnSocket()

        return messageBuffer
    }
}
