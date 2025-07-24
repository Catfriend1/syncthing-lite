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

import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager

/**
 * Custom KeyManager that forces the selection of a specific certificate alias.
 * 
 * This is a workaround for Syncthing v2.x TLS handshake requirements where the
 * default Android/JVM KeyManager selection logic doesn't reliably choose the
 * correct client certificate during TLS handshake. This KeyManager always
 * returns the "key" alias, ensuring our custom certificate is presented to
 * Syncthing servers.
 */
class ForcedKeyManager(
    private val delegate: X509ExtendedKeyManager,
    private val forcedAlias: String = "key"
) : X509ExtendedKeyManager() {

    companion object {
        private val logger = LoggerFactory.getLogger(ForcedKeyManager::class.java)
    }

    override fun chooseClientAlias(keyType: Array<String>?, issuers: Array<Principal>?, socket: Socket?): String? {
        logger.error("🔑 ForcedKeyManager.chooseClientAlias CALLED - keyType: ${keyType?.joinToString()}, forcedAlias: $forcedAlias")
        // Verify the certificate and private key are accessible
        val cert = delegate.getCertificateChain(forcedAlias)
        val key = delegate.getPrivateKey(forcedAlias)
        logger.error("🔑 Certificate available: ${cert != null}, PrivateKey available: ${key != null}")
        if (cert != null) {
            cert.forEach { x509 ->
                if (x509 is X509Certificate) {
                    logger.error("🔑 Certificate Subject: ${x509.subjectDN}")
                    logger.error("🔑 Certificate SigAlg: ${x509.sigAlgName}")
                }
            }
        }
        return forcedAlias
    }

    override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: Socket?): String? {
        logger.error("🔑 ForcedKeyManager.chooseServerAlias CALLED - keyType: $keyType, forcedAlias: $forcedAlias")
        return forcedAlias
    }

    override fun chooseEngineClientAlias(keyType: Array<String>?, issuers: Array<Principal>?, engine: SSLEngine?): String? {
        logger.error("🔑 ForcedKeyManager.chooseEngineClientAlias CALLED - keyType: ${keyType?.joinToString()}, forcedAlias: $forcedAlias")
        // Verify the certificate and private key are accessible
        val cert = delegate.getCertificateChain(forcedAlias)
        val key = delegate.getPrivateKey(forcedAlias)
        logger.error("🔑 Certificate available: ${cert != null}, PrivateKey available: ${key != null}")
        if (cert != null) {
            cert.forEach { x509 ->
                if (x509 is X509Certificate) {
                    logger.error("🔑 Certificate Subject: ${x509.subjectDN}")
                    logger.error("🔑 Certificate SigAlg: ${x509.sigAlgName}")
                }
            }
        }
        return forcedAlias
    }

    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<Principal>?, engine: SSLEngine?): String? {
        logger.error("🔑 ForcedKeyManager.chooseEngineServerAlias CALLED - keyType: $keyType, forcedAlias: $forcedAlias")
        return forcedAlias
    }

    // Delegate all other methods to the wrapped KeyManager
    override fun getClientAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? {
        return delegate.getClientAliases(keyType, issuers)
    }

    override fun getServerAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? {
        return delegate.getServerAliases(keyType, issuers)
    }

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
        return delegate.getCertificateChain(alias)
    }

    override fun getPrivateKey(alias: String?): PrivateKey? {
        return delegate.getPrivateKey(alias)
    }
}