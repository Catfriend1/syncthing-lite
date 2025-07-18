/*
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
package net.syncthing.java.discovery

import kotlinx.coroutines.channels.*
import net.syncthing.java.core.beans.DeviceAddress
import net.syncthing.java.core.beans.DeviceId

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DeviceAddressesManager(val deviceId: DeviceId) {

    companion object {
        private const val MAX_ADDRESSES_PER_TYPE = 16
    }

    private val lock = Object()
    private val deviceAddressesCache = mutableListOf<DeviceAddress>()
    private val listeners = mutableListOf<(DeviceAddress) -> Unit>()

    fun putAddress(address: DeviceAddress) {
        if (address.deviceId != deviceId) {
            throw IllegalArgumentException()
        }

        synchronized(lock) {
            val otherAddressesOfSameType = deviceAddressesCache.filter { it.type == address.type }

            if (otherAddressesOfSameType.size == MAX_ADDRESSES_PER_TYPE) {
                // forget the oldest one of the same type
                deviceAddressesCache.remove(otherAddressesOfSameType.first())
            }

            deviceAddressesCache.add(address)
            listeners.forEach { it(address) }
        }
    }

    private fun addListener(listener: (DeviceAddress) -> Unit) {
        synchronized(lock) {
            listeners.add(listener)
        }
    }

    private fun removeListener(listener: (DeviceAddress) -> Unit) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    // this creates a copy of the set
    fun getCurrentDeviceAddresses(): List<DeviceAddress> = synchronized(lock) {
        deviceAddressesCache.toList()
    }

    fun streamCurrentDeviceAddresses(): ReceiveChannel<DeviceAddress> = Channel<DeviceAddress>(capacity = Channel.UNLIMITED).apply {
        val listener: (DeviceAddress) -> Unit = {
            trySend(it)
        }

        invokeOnClose {
            removeListener(listener)
        }

        synchronized(lock) {
            addListener(listener)
            deviceAddressesCache.forEach { listener(it) }
        }
    }
}
