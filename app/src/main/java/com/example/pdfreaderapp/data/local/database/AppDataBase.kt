package com.example.pdfreaderapp.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.pdfreaderapp.data.local.dao.PdfDao
import com.example.pdfreaderapp.data.local.entity.QaEntity
import com.example.pdfreaderapp.data.local.entity.SummaryEntity




@Database(
    entities = [SummaryEntity ::class, QaEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pdfDao(): PdfDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pdf_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}