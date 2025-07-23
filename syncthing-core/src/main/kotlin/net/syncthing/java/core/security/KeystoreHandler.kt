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
import net.syncthing.java.core.interfaces.RelayConnection
import net.syncthing.java.core.utils.NetworkUtils
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
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.OperatorCreationException
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.encoders.Base64
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.NamedParameterSpec
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal

class KeystoreHandler private constructor(private val keyStore: KeyStore) {

    class CryptoException internal constructor(t: Throwable) : GeneralSecurityException(t)

    private val socketFactory: SSLSocketFactory

    init {
        val sslContext = SSLContext.getInstance(TLS_VERSION)
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
    private fun wrapSocket(socket: Socket, isServerSocket: Boolean): SSLSocket {
        try {
            logger.debug("Wrapping plain socket, server mode: {}.", isServerSocket)
            val sslSocket = socketFactory.createSocket(socket, null, socket.port, true) as SSLSocket
            if (isServerSocket) {
                sslSocket.useClientMode = false
            }
            return sslSocket
        } catch (e: KeyManagementException) {
            throw CryptoException(e)
        } catch (e: NoSuchAlgorithmException) {
            throw CryptoException(e)
        } catch (e: KeyStoreException) {
            throw CryptoException(e)
        } catch (e: UnrecoverableKeyException) {
            throw CryptoException(e)
        }

    }

    @Throws(CryptoException::class, IOException::class)
    fun createSocket(relaySocketAddress: InetSocketAddress): SSLSocket {
        try {
            val socket = socketFactory.createSocket() as SSLSocket
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
        }
    }

    @Throws(CryptoException::class, IOException::class)
    fun wrapSocket(relayConnection: RelayConnection): SSLSocket {
        return wrapSocket(relayConnection.getSocket(), relayConnection.isServerSocket())
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
                val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGO, BouncyCastleProvider.PROVIDER_NAME)
                keyPairGenerator.initialize(NamedParameterSpec(KEY_ALGO))
                val keyPair = keyPairGenerator.genKeyPair()

                val contentSigner = JcaContentSignerBuilder(SIGNATURE_ALGO)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(keyPair.private)

                val startDate = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
                val endDate = Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365 * 10))

                val subject = X500Principal(CERTIFICATE_SUBJECT)
                val certificateBuilder = JcaX509v1CertificateBuilder(
                    subject,
                    BigInteger.ZERO,
                    startDate,
                    endDate,
                    subject,
                    keyPair.public
                )

                val certHolder = certificateBuilder.build(contentSigner)

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
                    GeneralNames(GeneralName(GeneralName.dNSName, "syncthing"))
                )

                val certHolderFinal = certBuilder.build(contentSigner)

                val certificateDerData = certHolderFinal.encoded
                // logger.trace("Generated certificate: {}.", derToPem(certificateDerData))
                val deviceId = derDataToDeviceId(certificateDerData)
                // logger.trace("Device ID from certificate: {}.", deviceId)

                val keyStore = KeyStore.getInstance(keystoreAlgorithm)
                keyStore.load(null, null)
                val certChain = arrayOf(
                    JcaX509CertificateConverter()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .getCertificate(certHolderFinal)
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
                val deviceId = derDataToDeviceId(derData)
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
        private const val KEY_ALGO = "EC"
        private const val SIGNATURE_ALGO = "SHA256withECDSA"
        private const val CERTIFICATE_SUBJECT = "CN=syncthing, OU=Automatically Generated, O=Syncthing"
        private const val SOCKET_TIMEOUT = 2000
        private const val TLS_VERSION = "TLSv1.3"

        init {
            Security.addProvider(BouncyCastleProvider())
        }

        private fun derToPem(der: ByteArray): String {
            return "-----BEGIN CERTIFICATE-----\n" + Base64.toBase64String(der).chunked(76).joinToString("\n") + "\n-----END CERTIFICATE-----"
        }

        fun derDataToDeviceId(certificateDerData: ByteArray): DeviceId {
            return DeviceId.fromHashData(MessageDigest.getInstance("SHA-256").digest(certificateDerData))
        }

        const val BEP = "bep/1.0"
        const val RELAY = "bep-relay"

        private val logger = LoggerFactory.getLogger(KeystoreHandler::class.java)

        @Throws(SSLPeerUnverifiedException::class, CertificateException::class)
        fun assertSocketCertificateValid(socket: SSLSocket, deviceId: DeviceId) {
            val session = socket.session
            val certs = session.peerCertificates.toList()
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val certPath = certificateFactory.generateCertPath(certs)
            val certificate = certPath.certificates[0]

            assertSocketCertificateValid(certificate, deviceId)
        }

        @Throws(SSLPeerUnverifiedException::class, CertificateException::class)
        fun assertSocketCertificateValid(certificate: Certificate, deviceId: DeviceId) {
            NetworkUtils.assertProtocol(certificate is X509Certificate)

            val derData = certificate.encoded
            val deviceIdFromCertificate = derDataToDeviceId(derData)
            // logger.trace("Remote PEM Certificate: {}.", derToPem(derData))

            NetworkUtils.assertProtocol(deviceIdFromCertificate == deviceId) {
                "Device ID mismatch! Expected = $deviceId, Received = $deviceIdFromCertificate."
            }
            logger.debug("Remote SSL certificate match deviceId: {}.", deviceId)
        }
    }
}
