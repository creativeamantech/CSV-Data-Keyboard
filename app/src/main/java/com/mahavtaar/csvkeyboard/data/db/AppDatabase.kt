package com.mahavtaar.csvkeyboard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mahavtaar.csvkeyboard.data.model.ColumnConfig
import com.mahavtaar.csvkeyboard.data.model.CsvRow

@Database(entities = [CsvRow::class, ColumnConfig::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun csvRowDao(): CsvRowDao
    abstract fun columnConfigDao(): ColumnConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "csv_keyboard_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
