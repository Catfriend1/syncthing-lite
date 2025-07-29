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

import net.syncthing.java.client.protocol.rp.RelayClient
import net.syncthing.java.core.beans.DeviceAddress
import net.syncthing.java.core.configuration.Configuration
import net.syncthing.java.core.security.KeystoreHandler
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import javax.net.ssl.SSLSocket

object OpenConnection {
    private const val ALPN_BEP = "bep/1.0"

    private val logger = LoggerFactory.getLogger(OpenConnection::class.java)

    fun openSocketConnection(
            address: DeviceAddress,
            configuration: Configuration
    ): SSLSocket {
        val keystoreHandler = KeystoreHandler.Loader().loadKeystore(configuration)

        return when (address.type) {
            DeviceAddress.AddressType.TCP -> {
                logger.debug("Opening TCP SSL connection at address {}.", address)
                keystoreHandler.createSocket(address.getSocketAddress(), ALPN_BEP)
            }
            DeviceAddress.AddressType.RELAY -> {
                logger.debug("Opening relay connection at relay {}.", address)
                val relayConnection = RelayClient(configuration).openRelayConnection(address)
                keystoreHandler.wrapSocket(relayConnection.getSocket(), relayConnection.isServerSocket())
            }
            else -> {
                val message = "Unsupported address type: ${address.type}."
                logger.warn(message)
                throw UnsupportedOperationException(message)
            }
        }
    }
}
