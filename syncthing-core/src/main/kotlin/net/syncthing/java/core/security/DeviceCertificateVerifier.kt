package net.syncthing.java.core.security

import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.utils.NetworkUtils
import net.syncthing.java.core.utils.LoggerFactory
import org.bouncycastle.util.encoders.Base64
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket

object DeviceCertificateVerifier {

    private val logger = LoggerFactory.getLogger(DeviceCertificateVerifier::class.java)

    fun derToPem(der: ByteArray): String {
        return "-----BEGIN CERTIFICATE-----\n" +
            Base64.toBase64String(der).chunked(76).joinToString("\n") +
            "\n-----END CERTIFICATE-----"
    }

    fun derDataToDeviceId(certificateDerData: ByteArray): DeviceId {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificateDerData)
        return DeviceId.fromHashData(digest)
    }

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

        logger.debug("Remote SSL certificate matches expected device ID: $deviceId")
    }
}
