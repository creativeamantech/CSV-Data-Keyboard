package com.mahavtaar.csvkeyboard.data.csv

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mahavtaar.csvkeyboard.data.db.AppDatabase
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
                SessionBus.emitRowChange(context, newIndex)
            }
        }
    }

    fun saveUri(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        AppPreferences.save(context, AppPreferences.KEY_CSV_URI, uri.toString())
    }

    fun loadSavedUri(context: Context): Uri? {
        val uriString = AppPreferences.getString(context, AppPreferences.KEY_CSV_URI, null)
            ?: return null
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.close()
            uri
        } catch (e: Exception) {
            AppPreferences.save(context, AppPreferences.KEY_CSV_URI, null)
            null
        }
    }

    suspend fun saveSessionData(context: Context, headers: List<String>, rows: List<CsvRow>, filePath: String) {
        AppPreferences.save(context, AppPreferences.KEY_CSV_HEADERS, Json.encodeToString(headers))
        saveUri(context, Uri.parse(filePath))

        val db = AppDatabase.getDatabase(context)
        db.csvRowDao().deleteAll()
        db.csvRowDao().insertAll(rows)

        SessionBus.emitRowChange(context, 0)
    }

    suspend fun loadSession(context: Context): KeyboardSession? {
        val uri = loadSavedUri(context) ?: return null
        val headersStr = AppPreferences.getString(context, AppPreferences.KEY_CSV_HEADERS) ?: return null
        val index = AppPreferences.getInt(context, AppPreferences.KEY_CURRENT_ROW, 0)

        return try {
            val headers = Json.decodeFromString<List<String>>(headersStr)
            val db = AppDatabase.getDatabase(context)
            val rows = db.csvRowDao().getAll()
            KeyboardSession(uri.toString(), headers, rows, index)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateRow(context: Context, row: CsvRow) {
        val db = AppDatabase.getDatabase(context)
        db.csvRowDao().updateRow(row)
        currentSession?.let {
            val rows = it.rows.toMutableList()
            rows[row.rowIndex] = row
            currentSession = it.copy(rows = rows)
        }
    }
}
