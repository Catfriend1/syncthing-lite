package net.syncthing.repository.android.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import net.syncthing.repository.android.database.dao.*
import net.syncthing.repository.android.database.item.*

@Database(
        version = 1,
        entities = [
            FileBlocksItem::class,
            FileInfoItem::class,
            FolderIndexInfoItem::class,
            FolderStatsItem::class,
            IndexSequenceItem::class
        ]
)
abstract class RepositoryDatabase: RoomDatabase() {
    companion object {
        private var instance: RepositoryDatabase? = null
        private val lock = Object()

        fun createInstance(context: Context, name: String) = Room.databaseBuilder(
                context.applicationContext,
                RepositoryDatabase::class.java,
                name
        ).build()

        fun createInMemoryInstance(context: Context) = Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                RepositoryDatabase::class.java
        ).build()

        fun with(context: Context): RepositoryDatabase {
            println("[RepositoryDatabase] entering with()")
            if (instance == null) {
                synchronized (lock) {
                    println("[RepositoryDatabase] entering synchronized block")
                    if (instance == null) {
                        println("[RepositoryDatabase] creating Room database")
                        instance = createInstance(context, "repository_database")
                        println("[RepositoryDatabase] instance assigned")
                    }
                }
            }
            println("[RepositoryDatabase] returning instance")
            return instance!!
        }
    }

    abstract fun fileInfo(): FileInfoDao
    abstract fun fileBlocks(): FileBlocksDao
    abstract fun folderStats(): FolderStatsDao
    abstract fun folderIndexInfo(): FolderIndexInfoDao
    abstract fun indexSequence(): IndexSequenceDao
}
