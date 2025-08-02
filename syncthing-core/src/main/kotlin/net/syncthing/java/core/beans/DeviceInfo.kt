/*
 * Copyright 2016 Davide Imbriaco <davide.imbriaco@gmail.com>
 * Copyright 2018 Jonas Lochmann
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.syncthing.java.core.beans

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.Serializable

data class DeviceInfo(val deviceId: DeviceId, val name: String, val addresses: List<String> = listOf("dynamic")): Serializable {

    companion object {
        private const val DEVICE_ID = "deviceId"
        private const val NAME = "name"
        private const val ADDRESSES = "addresses"

        fun parse(reader: JsonReader): DeviceInfo {
            var deviceId: DeviceId? = null
            var name: String? = null
            var addresses: List<String> = listOf("dynamic")  // default value

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    DEVICE_ID -> deviceId = DeviceId.parse(reader)
                    NAME -> name = reader.nextString()
                    ADDRESSES -> {
                        val addressList = mutableListOf<String>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            addressList.add(reader.nextString())
                        }
                        reader.endArray()
                        addresses = addressList.ifEmpty { listOf("dynamic") }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return DeviceInfo(
                    deviceId = deviceId!!,
                    name = name!!,
                    addresses = addresses
            )
        }
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(DEVICE_ID)
        deviceId.serialize(writer)

        writer.name(NAME).value(name)

        writer.name(ADDRESSES).beginArray()
        addresses.forEach { writer.value(it) }
        writer.endArray()

        writer.endObject()
    }
}
