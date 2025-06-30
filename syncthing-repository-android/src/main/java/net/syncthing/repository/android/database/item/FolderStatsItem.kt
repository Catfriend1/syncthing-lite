package net.syncthing.repository.android.database.item

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import net.syncthing.java.core.beans.FolderStats
import net.syncthing.repository.android.database.converters.DateConverter
import java.util.*

@Entity(
        tableName = "folder_stats"
)
@TypeConverters(DateConverter::class)
data class FolderStatsItem(
        @PrimaryKey
        val folder: String,
        @ColumnInfo(name = "file_count")
        val fileCount: Long,
        @ColumnInfo(name = "dir_count")
        val dirCount: Long,
        @ColumnInfo(name = "last_update")
        val lastUpdate: Date,
        val size: Long
) {
    @delegate:Transient
    val native: FolderStats by lazy {
        FolderStats(
                folderId = folder,
                dirCount = dirCount,
                fileCount = fileCount,
                size = size,
                lastUpdate = lastUpdate
        )
    }
}
