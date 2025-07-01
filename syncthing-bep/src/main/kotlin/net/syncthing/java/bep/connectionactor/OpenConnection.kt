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
import org.apache.logging.log4j.LogManager
import javax.net.ssl.SSLSocket

object OpenConnection {
    private val LOGGER = LogManager.getLogger(OpenConnection::class.java)
    fun openSocketConnection(
            address: DeviceAddress,
            configuration: Configuration
    ): SSLSocket {
        val keystoreHandler = KeystoreHandler.Loader().loadKeystore(configuration)

        return when (address.type) {
            DeviceAddress.AddressType.TCP -> {
                LOGGER.atDebug().log("Opening TCP SSL connection at address {}.", address)
                keystoreHandler.createSocket(address.getSocketAddress())
            }
            DeviceAddress.AddressType.RELAY -> {
                LOGGER.atDebug().log("Opening relay connection at relay {}.", address)
                keystoreHandler.wrapSocket(RelayClient(configuration).openRelayConnection(address))
            }
            else -> {
                val message = "Unsupported address type: ${address.type}."
                LOGGER.atWarn().log(message)
                throw UnsupportedOperationException(message)
            }
        }
    }
}
