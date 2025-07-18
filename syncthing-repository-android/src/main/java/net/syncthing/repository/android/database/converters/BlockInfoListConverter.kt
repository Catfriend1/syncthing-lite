package net.syncthing.repository.android.database.converters

import androidx.room.TypeConverter
import com.google.protobuf.ByteString
import net.syncthing.java.bep.BlockExchangeExtraProtos
import net.syncthing.java.bep.BlockExchangeProtos
import net.syncthing.java.core.beans.BlockInfo
import org.bouncycastle.util.encoders.Hex

// the original implementation used this approach too
class BlockInfoListConverter {
    @TypeConverter
    fun toByteArray(blockInfos: List<BlockInfo>) = BlockExchangeExtraProtos.Blocks.newBuilder()
            .addAllBlocks(blockInfos.map { input ->
                BlockExchangeProtos.BlockInfo.newBuilder()
                        .setOffset(input.offset)
                        .setSize(input.size)
                        .setHash(ByteString.copyFrom(Hex.decode(input.hash)))
                        .build()
            }).build().toByteArray()

    @TypeConverter
    fun fromString(data: ByteArray?): List<BlockInfo> {
        return if (data == null) {
            emptyList()
        } else {
            BlockExchangeExtraProtos.Blocks.parseFrom(data).blocksList.map { record ->
                BlockInfo(record!!.offset, record.size, Hex.toHexString(record.hash.toByteArray()))
            }
        }
    }
}
