package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        WorkAreaEntity::class,
        DocumentEntity::class,
        DocumentSnapshotEntity::class,
        BrainItemEntity::class,
        EvidenceLinkEntity::class,
        ChangeTrailEntity::class,
        FileOperationJournalEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CompassDatabase : RoomDatabase() {
    abstract fun compassDao(): CompassDao

    companion object {
        @Volatile
        private var INSTANCE: CompassDatabase? = null

        fun getDatabase(context: Context): CompassDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CompassDatabase::class.java,
                    "project_compass_local.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
