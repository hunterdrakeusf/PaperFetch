package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.PaperRecord

@Database(entities = [PaperRecord::class], version = 1, exportSchema = false)
abstract class PaperDatabase : RoomDatabase() {

    abstract fun paperDao(): PaperDao

    companion object {
        @Volatile
        private var INSTANCE: PaperDatabase? = null

        fun getInstance(context: Context): PaperDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaperDatabase::class.java,
                    "paper_fetch_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
