package com.mahavtaar.csvkeyboard.data.prefs

import android.content.Context
import com.mahavtaar.csvkeyboard.data.db.AppDatabase
import com.mahavtaar.csvkeyboard.data.model.ColumnConfig
import com.mahavtaar.csvkeyboard.data.model.ColumnMode

object ColumnConfigStore {
    suspend fun save(context: Context, configs: List<ColumnConfig>) {
        val db = AppDatabase.getDatabase(context)
        db.columnConfigDao().deleteAll()
        db.columnConfigDao().insertAll(configs)
    }

    suspend fun load(context: Context): List<ColumnConfig> {
        val db = AppDatabase.getDatabase(context)
        return db.columnConfigDao().getAll()
    }

    fun generateDefaults(headers: List<String>): List<ColumnConfig> {
        return headers.mapIndexed { index, header ->
            val mode = if (index < 3) ColumnMode.TYPE else ColumnMode.INFO
            ColumnConfig(
                columnName = header,
                mode = mode,
                displayLabel = header,
                order = index,
                colorHex = if (mode == ColumnMode.TYPE) "#0F3460" else "#FFFFFF"
            )
        }
    }
}
