package com.example.projectneptune.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Layer20Feature::class, Station::class, TideData::class, StaticBoundary::class, CatchLimit::class, AppMetadata::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mapDao(): MapDao

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "neptune_database")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}
