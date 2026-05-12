package com.mahavtaar.csvkeyboard.data.csv

import android.content.Context
import com.mahavtaar.csvkeyboard.data.model.CsvRow
import com.mahavtaar.csvkeyboard.data.model.KeyboardSession
import com.mahavtaar.csvkeyboard.data.prefs.AppPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CsvRepository {
    private var currentSession: KeyboardSession? = null

    fun initSession(session: KeyboardSession) {
        currentSession = session
    }

    fun getSession(): KeyboardSession? = currentSession

    fun updateIndex(context: Context, newIndex: Int) {
        currentSession?.let {
            if (newIndex in 0 until it.totalRows) {
                currentSession = it.copy(currentIndex = newIndex)
                AppPreferences.save(context, AppPreferences.KEY_CURRENT_ROW, newIndex)
                SessionBus.rowChanged.tryEmit(newIndex)
            }
        }
    }

    fun saveSessionData(context: Context, headers: List<String>, rows: List<CsvRow>, filePath: String) {
        AppPreferences.save(context, AppPreferences.KEY_CSV_HEADERS, Json.encodeToString(headers))
        // Basic optimization: if rows is small, save to prefs. For this spec, we will save to prefs to keep it simple.
        AppPreferences.save(context, AppPreferences.KEY_CSV_ROWS, Json.encodeToString(rows))
        AppPreferences.save(context, AppPreferences.KEY_CSV_URI, filePath)
        AppPreferences.save(context, AppPreferences.KEY_CURRENT_ROW, 0)
    }

    fun loadSessionFromPrefs(context: Context): KeyboardSession? {
        val uriStr = AppPreferences.getString(context, AppPreferences.KEY_CSV_URI) ?: return null
        val headersStr = AppPreferences.getString(context, AppPreferences.KEY_CSV_HEADERS) ?: return null
        val rowsStr = AppPreferences.getString(context, AppPreferences.KEY_CSV_ROWS) ?: return null
        val index = AppPreferences.getInt(context, AppPreferences.KEY_CURRENT_ROW, 0)

        return try {
            val headers = Json.decodeFromString<List<String>>(headersStr)
            val rows = Json.decodeFromString<List<CsvRow>>(rowsStr)
            KeyboardSession(uriStr, headers, rows, index)
        } catch (e: Exception) {
            null
        }
    }
}
