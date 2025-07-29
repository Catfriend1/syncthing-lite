package net.syncthing.java.core.utils

import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import net.syncthing.java.core.utils.NetworkUtils
import org.bouncycastle.util.encoders.Base64

object CertUtils {

    fun convertPrivateKeyToPem(privateKey: PrivateKey): String {
        val base64 = Base64.toBase64String(privateKey.encoded)
        return "-----BEGIN PRIVATE KEY-----\n" +
            base64.chunked(76).joinToString("\n") +
            "\n-----END PRIVATE KEY-----"
    }

    fun convertCertificateToPem(der: ByteArray): String {
        return "-----BEGIN CERTIFICATE-----\n" +
            Base64.toBase64String(der).chunked(76).joinToString("\n") +
            "\n-----END CERTIFICATE-----"
    }

    fun parseCertificateFromPem(pem: String): X509Certificate {
        val certFactory = CertificateFactory.getInstance("X.509")
        val input = ByteArrayInputStream(
            pem.lines().filterNot { it.startsWith("-----") }.joinToString("").decodeBase64()
        )
        val certificate = certFactory.generateCertificate(input)
        NetworkUtils.assertProtocol(certificate is X509Certificate)
        return certificate as X509Certificate
    }

    fun parsePrivateKeyFromPem(pem: String, keyAlgo: String): PrivateKey {
        val keySpec = PKCS8EncodedKeySpec(
            pem.lines().filterNot { it.startsWith("-----") }.joinToString("").decodeBase64()
        )
        val kf = KeyFactory.getInstance(keyAlgo)
        return kf.generatePrivate(keySpec)
    }

    private fun String.decodeBase64() = Base64.decode(this)

}
