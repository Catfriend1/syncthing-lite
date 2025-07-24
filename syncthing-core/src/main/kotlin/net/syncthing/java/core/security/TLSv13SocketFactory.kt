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
package net.syncthing.java.core.security

import net.syncthing.java.core.utils.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Custom SSLSocketFactory that forces all sockets to use TLSv1.3 only.
 * 
 * This ensures that the ClientHello message in the TLS handshake advertises
 * only TLSv1.3 support, which is required for compatibility with Syncthing v2.x.
 */
class TLSv13SocketFactory(
    private val delegate: SSLSocketFactory
) : SSLSocketFactory() {

    companion object {
        private val logger = LoggerFactory.getLogger(TLSv13SocketFactory::class.java)
        
        // TLSv1.3 cipher suites observed in working syncthing v1->v2 connections
        private val TLS_V13_CIPHER_SUITES = arrayOf(
            "TLS_AES_128_GCM_SHA256",
            "TLS_AES_256_GCM_SHA384", 
            "TLS_CHACHA20_POLY1305_SHA256"
        )
        
        private val TLS_V13_PROTOCOLS = arrayOf("TLSv1.3")
    }

    private fun configureTLSv13Socket(socket: Socket): Socket {
        if (socket is SSLSocket) {
            logger.error("🔧 Configuring socket for TLSv1.3-only mode")
            
            // Force TLSv1.3 only
            socket.enabledProtocols = TLS_V13_PROTOCOLS
            logger.error("🔧 Set enabled protocols: ${socket.enabledProtocols.joinToString()}")
            
            // Set TLSv1.3 cipher suites
            val supportedCiphers = socket.supportedCipherSuites
            val filteredCiphers = TLS_V13_CIPHER_SUITES.filter { it in supportedCiphers }
            
            if (filteredCiphers.isNotEmpty()) {
                socket.enabledCipherSuites = filteredCiphers.toTypedArray()
                logger.error("🔧 Set TLSv1.3 cipher suites: ${filteredCiphers.joinToString()}")
            } else {
                logger.error("❌ No TLSv1.3 cipher suites available!")
            }
            
            // Set ALPN for BEP protocol
            try {
                val sslParams = socket.sslParameters
                sslParams.applicationProtocols = arrayOf("bep/1.0")
                socket.sslParameters = sslParams
                logger.error("🔗 Set ALPN protocol: bep/1.0")
            } catch (e: Exception) {
                logger.error("⚠️ Failed to set ALPN protocol: ${e.message}")
            }
        }
        return socket
    }

    override fun createSocket(): Socket {
        return configureTLSv13Socket(delegate.createSocket())
    }

    override fun createSocket(host: String?, port: Int): Socket {
        return configureTLSv13Socket(delegate.createSocket(host, port))
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        return configureTLSv13Socket(delegate.createSocket(host, port, localHost, localPort))
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        return configureTLSv13Socket(delegate.createSocket(host, port))
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        return configureTLSv13Socket(delegate.createSocket(address, port, localAddress, localPort))
    }

    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
        return configureTLSv13Socket(delegate.createSocket(s, host, port, autoClose))
    }

    override fun getDefaultCipherSuites(): Array<String> {
        return TLS_V13_CIPHER_SUITES
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return delegate.supportedCipherSuites
    }
}