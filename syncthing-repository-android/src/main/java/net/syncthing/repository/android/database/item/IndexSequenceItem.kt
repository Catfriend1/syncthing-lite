package net.syncthing.repository.android.database.item

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
        tableName = "index_sequence"
)
data class IndexSequenceItem(
        @PrimaryKey
        @ColumnInfo(name = "index_id")
        val indexId: Long,
        @ColumnInfo(name = "current_sequence")
        val currentSequence: Long
)
