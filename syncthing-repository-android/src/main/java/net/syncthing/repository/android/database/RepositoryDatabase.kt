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
abstract class RepositoryDatabase : RoomDatabase() {
    companion object {
        private var instance: RepositoryDatabase? = null
        private val lock = Object()

        // Umschaltbar zwischen persistenter DB und In-Memory
        private const val USE_IN_MEMORY_DB_FOR_TESTING = true

        fun createInstance(context: Context, name: String): RepositoryDatabase {
            println("[RepositoryDatabase] createInstance() called — mode: ${if (USE_IN_MEMORY_DB_FOR_TESTING) "inMemory" else "file"}")

            return if (USE_IN_MEMORY_DB_FOR_TESTING) {
                createInMemoryInstance(context)
            } else {
                Room.databaseBuilder(
                        context.applicationContext,
                        RepositoryDatabase::class.java,
                        name
                )
                .fallbackToDestructiveMigration()
                .build()
            }
        }

        fun createInMemoryInstance(context: Context): RepositoryDatabase {
            println("[RepositoryDatabase] createInMemoryInstance() called")
            return Room.inMemoryDatabaseBuilder(
                    context.applicationContext,
                    RepositoryDatabase::class.java
            ).build()
        }

        fun with(context: Context): RepositoryDatabase {
            println("[RepositoryDatabase] entering with()")
            if (instance == null) {
                synchronized(lock) {
                    println("[RepositoryDatabase] entering synchronized block")
                    if (instance == null) {
                        println("[RepositoryDatabase] creating database instance…")
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
