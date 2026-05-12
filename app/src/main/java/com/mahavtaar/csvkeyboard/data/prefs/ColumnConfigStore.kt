package com.mahavtaar.csvkeyboard.data.prefs

import android.content.Context
import com.mahavtaar.csvkeyboard.data.model.ColumnConfig
import com.mahavtaar.csvkeyboard.data.model.ColumnMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ColumnConfigStore {
    fun save(context: Context, configs: List<ColumnConfig>) {
        val json = Json.encodeToString(configs)
        AppPreferences.save(context, AppPreferences.KEY_COLUMN_CONFIGS, json)
    }

    fun load(context: Context): List<ColumnConfig>? {
        val json = AppPreferences.getString(context, AppPreferences.KEY_COLUMN_CONFIGS)
        if (json.isNullOrEmpty()) return null
        return try {
            Json.decodeFromString<List<ColumnConfig>>(json)
        } catch (e: Exception) {
            null
        }
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
