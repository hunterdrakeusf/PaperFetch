package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.PaperRecord

@Database(entities = [PaperRecord::class], version = 2, exportSchema = false)
abstract class PaperDatabase : RoomDatabase() {

    abstract fun paperDao(): PaperDao

    companion object {
        @Volatile
        private var INSTANCE: PaperDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE paper_records ADD COLUMN volume TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE paper_records ADD COLUMN issue TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE paper_records ADD COLUMN pages TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE paper_records ADD COLUMN issn TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE paper_records ADD COLUMN publisher TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE paper_records ADD COLUMN url TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE paper_records ADD COLUMN abstractText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE paper_records ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): PaperDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaperDatabase::class.java,
                    "paper_fetch_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
