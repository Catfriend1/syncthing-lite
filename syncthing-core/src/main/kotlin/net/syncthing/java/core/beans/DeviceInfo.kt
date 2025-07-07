package net.syncthing.java.core.beans

import android.os.Parcelable
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeviceInfo(
    val deviceId: DeviceId,
    val name: String
) : Parcelable {

    companion object {
        private const val DEVICE_ID = "deviceId"
        private const val NAME = "name"

        fun parse(reader: JsonReader): DeviceInfo {
            var deviceId: DeviceId? = null
            var name: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    DEVICE_ID -> deviceId = DeviceId.parse(reader)
                    NAME -> name = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return DeviceInfo(
                deviceId = deviceId!!,
                name = name!!
            )
        }
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()
        writer.name(DEVICE_ID)
        deviceId.serialize(writer)
        writer.name(NAME).value(name)
        writer.endObject()
    }
}
