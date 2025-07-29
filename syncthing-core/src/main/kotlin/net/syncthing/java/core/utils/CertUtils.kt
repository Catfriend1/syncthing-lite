package net.syncthing.java.core.utils

import org.bouncycastle.util.encoders.Base64
import java.security.PrivateKey

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
}
