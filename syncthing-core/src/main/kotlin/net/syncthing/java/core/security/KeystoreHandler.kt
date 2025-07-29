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

import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.configuration.Configuration
import net.syncthing.java.core.security.DeviceCertificateVerifier
import net.syncthing.java.core.utils.NetworkUtils
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import org.apache.commons.codec.binary.Base32
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jsse.BCSSLSocket
import org.bouncycastle.jsse.BCSSLParameters
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import org.bouncycastle.operator.OperatorCreationException
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.KeyManagementException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.Security
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

class KeystoreHandler private constructor(private val keyStore: KeyStore) {

    class CryptoException internal constructor(t: Throwable) : GeneralSecurityException(t)

    private val socketFactory: SSLSocketFactory

    init {
        Security.addProvider(BouncyCastleJsseProvider())
        val sslContext = SSLContext.getInstance(TLS_VERSION, "BCJSSE")
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, KEY_PASSWORD.toCharArray())

        sslContext.init(keyManagerFactory.keyManagers, arrayOf(object : X509TrustManager {
            @Throws(CertificateException::class)
            override fun checkClientTrusted(xcs: Array<X509Certificate>, string: String) {}
            @Throws(CertificateException::class)
            override fun checkServerTrusted(xcs: Array<X509Certificate>, string: String) {}
            override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
        }), null)
        socketFactory = sslContext.socketFactory
    }

    @Throws(CryptoException::class, IOException::class)
    private fun exportKeystoreToData(): ByteArray {
        val out = ByteArrayOutputStream()
        try {
            keyStore.store(out, JKS_PASSWORD.toCharArray())
        } catch (ex: NoSuchAlgorithmException) {
            throw CryptoException(ex)
        } catch (ex: CertificateException) {
            throw CryptoException(ex)
        }
        return out.toByteArray()
    }

    @Throws(CryptoException::class, IOException::class)
    fun wrapSocket(socket: Socket, isServerSocket: Boolean): SSLSocket {
        try {
            logger.debug("Wrapping plain socket, server mode: {}.", isServerSocket)
            val socket = socketFactory.createSocket(socket, null, socket.port, true) as SSLSocket
            if (socket is BCSSLSocket) {
                val bcSocket = socket as BCSSLSocket
                val params = BCSSLParameters().apply {
                    applicationProtocols = arrayOf(ALPN_BEP)
                }
                bcSocket.parameters = params
            }
            if (isServerSocket) {
                socket.useClientMode = false
            }
            return socket
        } catch (e: KeyManagementException) {
            throw CryptoException(e)
        } catch (e: NoSuchAlgorithmException) {
            throw CryptoException(e)
        } catch (e: KeyStoreException) {
            throw CryptoException(e)
        } catch (e: UnrecoverableKeyException) {
            throw CryptoException(e)
        } catch (e: Exception) {
            logger.error("wrapSocket: Uncaught exception", e)
            throw Exception(e)
        }

    }

    @Throws(CryptoException::class, IOException::class)
    fun createSocket(relaySocketAddress: InetSocketAddress, applicationProtocol: String): SSLSocket {
        try {
            val socket = socketFactory.createSocket() as SSLSocket
            if (socket is BCSSLSocket) {
                val bcSocket = socket as BCSSLSocket
                val params = BCSSLParameters().apply {
                    applicationProtocols = arrayOf(applicationProtocol)
                }
                bcSocket.parameters = params
            }
            socket.connect(relaySocketAddress, SOCKET_TIMEOUT)
            return socket
        } catch (e: KeyManagementException) {
            throw CryptoException(e)
        } catch (e: NoSuchAlgorithmException) {
            throw CryptoException(e)
        } catch (e: KeyStoreException) {
            throw CryptoException(e)
        } catch (e: UnrecoverableKeyException) {
            throw CryptoException(e)
        } catch (e: Exception) {
            logger.error("createSocket: Uncaught exception", e)
            throw Exception(e)
        }
    }

    class Loader {

        private fun getKeystoreAlgorithm(keystoreAlgorithm: String?): String {
            return keystoreAlgorithm?.let { algo ->
                if (!algo.isBlank()) algo else null
            } ?: {
                val defaultAlgo = KeyStore.getDefaultType()!!
                logger.debug("Keystore algorithm set to {}.", defaultAlgo)
                defaultAlgo
            }()
        }

        @Throws(CryptoException::class, IOException::class)
        fun generateKeystore(): Triple<DeviceId, ByteArray, String> {
            val keystoreAlgorithm = getKeystoreAlgorithm(null)
            val keystore = generateKeystore(keystoreAlgorithm)
            val keystoreHandler = KeystoreHandler(keystore.first)
            val keystoreData = keystoreHandler.exportKeystoreToData()
            val hash = MessageDigest.getInstance("SHA-256").digest(keystoreData)
            keystoreHandlersCacheByHash[Base32().encodeAsString(hash)] = keystoreHandler
            logger.trace("Keystore is ready for device ID: {}.", keystore.second)
            return Triple(keystore.second, keystoreData, keystoreAlgorithm)
        }

        fun loadKeystore(configuration: Configuration): KeystoreHandler {
            val hash = MessageDigest.getInstance("SHA-256").digest(configuration.keystoreData)
            val keystoreHandlerFromCache = keystoreHandlersCacheByHash[Base32().encodeAsString(hash)]
            if (keystoreHandlerFromCache != null) {
                return keystoreHandlerFromCache
            }
            val keystoreAlgo = getKeystoreAlgorithm(configuration.keystoreAlgorithm)
            val keystore = importKeystore(configuration.keystoreData, keystoreAlgo)
            val keystoreHandler = KeystoreHandler(keystore.first)
            keystoreHandlersCacheByHash[Base32().encodeAsString(hash)] = keystoreHandler
            logger.trace("Keystore is ready for device ID: {}.", keystore.second)
            return keystoreHandler
        }

        @Throws(CryptoException::class, IOException::class)
        private fun generateKeystore(keystoreAlgorithm: String): Pair<KeyStore, DeviceId> {
            try {
                // logger.trace("Generating key.")
                val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGO)
                val keyPair = keyPairGenerator.generateKeyPair()

                val contentSigner = JcaContentSignerBuilder(SIGNATURE_ALGO).build(keyPair.private)

                val startDate = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
                val endDate = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(10 * 365))

                val subject = X500Principal(CERTIFICATE_SUBJECT)
                val certBuilder = JcaX509v3CertificateBuilder(subject, BigInteger.ONE, startDate, endDate, subject, keyPair.public)
                val extUtils = JcaX509ExtensionUtils()

                certBuilder.addExtension(
                    Extension.basicConstraints, true,
                    BasicConstraints(false) // Not a CA
                )

                certBuilder.addExtension(
                    Extension.keyUsage, true,
                    KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
                )

                certBuilder.addExtension(
                    Extension.extendedKeyUsage, false,
                    ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth))
                )

                certBuilder.addExtension(
                    Extension.subjectAlternativeName, false,
                    GeneralNames(GeneralName(GeneralName.dNSName, CERTIFICATE_DNS))
                )

                val certHolderFinal = certBuilder.build(contentSigner)

                val certificateDerData = certHolderFinal.encoded
                // logger.trace("Generated certificate: {}.", DeviceCertificateVerifier.derToPem(certificateDerData))
                val deviceId = DeviceCertificateVerifier.derDataToDeviceId(certificateDerData)
                // logger.trace("Device ID from certificate: {}.", deviceId)

                val keyStore = KeyStore.getInstance(keystoreAlgorithm)
                keyStore.load(null, null)
                val certChain = arrayOf(
                    JcaX509CertificateConverter().getCertificate(certHolderFinal)
                )
                keyStore.setKeyEntry("key", keyPair.private, KEY_PASSWORD.toCharArray(), certChain)
                return Pair(keyStore, deviceId)
            } catch (e: OperatorCreationException) {
                logger.trace("generateKeystore: OperatorCreationException", e)
                throw CryptoException(e)
            } catch (e: CertificateException) {
                logger.trace("generateKeystore: CertificateException", e)
                throw CryptoException(e)
            } catch (e: NoSuchAlgorithmException) {
                logger.trace("generateKeystore: NoSuchAlgorithmException", e)
                throw CryptoException(e)
            } catch (e: KeyStoreException) {
                logger.trace("generateKeystore: KeyStoreException", e)
                throw CryptoException(e)
            } catch (e: Exception) {
                logger.error("generateKeystore: Uncaught exception", e)
                throw Exception(e)
            }

        }

        @Throws(CryptoException::class, IOException::class)
        private fun importKeystore(keystoreData: ByteArray, keystoreAlgorithm: String): Pair<KeyStore, DeviceId> {
            try {
                val keyStore = KeyStore.getInstance(keystoreAlgorithm)
                keyStore.load(ByteArrayInputStream(keystoreData), JKS_PASSWORD.toCharArray())
                val alias = keyStore.aliases().nextElement()
                val certificate = keyStore.getCertificate(alias)
                NetworkUtils.assertProtocol(certificate is X509Certificate)
                val derData = certificate.encoded
                val deviceId = DeviceCertificateVerifier.derDataToDeviceId(derData)
                logger.debug("Loaded device ID from certificate: {}.", deviceId)
                return Pair(keyStore, deviceId)
            } catch (e: NoSuchAlgorithmException) {
                throw CryptoException(e)
            } catch (e: KeyStoreException) {
                throw CryptoException(e)
            } catch (e: CertificateException) {
                throw CryptoException(e)
            }

        }

        companion object {
            private val logger = LoggerFactory.getLogger(Loader::class.java)
            private val keystoreHandlersCacheByHash = mutableMapOf<String, KeystoreHandler>()
        }
    }

    companion object {

        private const val JKS_PASSWORD = "password"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ALGO = "Ed25519"
        private const val SIGNATURE_ALGO = "Ed25519"
        private const val CERTIFICATE_SUBJECT = "CN=syncthing, OU=Automatically Generated, O=Syncthing"
        private const val CERTIFICATE_DNS = "syncthing"
        private const val SOCKET_TIMEOUT = 2000
        private const val TLS_VERSION = "TLSv1.3"
        private const val ALPN_BEP = "bep/1.0"

        private val logger = LoggerFactory.getLogger(KeystoreHandler::class.java)
    }
}
