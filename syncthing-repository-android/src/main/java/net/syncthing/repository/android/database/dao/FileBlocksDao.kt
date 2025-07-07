package net.syncthing.repository.android.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.syncthing.repository.android.database.item.FileBlocksItem

@Dao
interface FileBlocksDao {
    @Query("SELECT * FROM file_blocks WHERE folder = :folder AND path = :path")
    fun findFileBlocks(folder: String, path: String): FileBlocksItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun mergeBlock(blocksItem: FileBlocksItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun mergeBlocks(blocksItem: List<FileBlocksItem>)
}
