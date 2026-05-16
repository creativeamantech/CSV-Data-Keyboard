package com.mahavtaar.csvkeyboard.data.csv

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mahavtaar.csvkeyboard.data.model.CsvRow
import com.mahavtaar.csvkeyboard.data.model.KeyboardSession
import com.mahavtaar.csvkeyboard.data.prefs.AppPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CsvRepository {
    fun isUriStillValid(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

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

    fun saveSessionData(context: Context, headers: List<String>, rows: List<CsvRow>, filePath: String) {
        AppPreferences.save(context, AppPreferences.KEY_CSV_HEADERS, Json.encodeToString(headers))
        AppPreferences.save(context, AppPreferences.KEY_CSV_ROWS, Json.encodeToString(rows))
        AppPreferences.save(context, AppPreferences.KEY_CURRENT_ROW, 0)
        saveUri(context, Uri.parse(filePath))
    }

    fun loadSessionFromPrefs(context: Context): KeyboardSession? {
        val uri = loadSavedUri(context) ?: return null
        val headersStr = AppPreferences.getString(context, AppPreferences.KEY_CSV_HEADERS) ?: return null
        val rowsStr = AppPreferences.getString(context, AppPreferences.KEY_CSV_ROWS) ?: return null
        val index = AppPreferences.getInt(context, AppPreferences.KEY_CURRENT_ROW, 0)

        return try {
            val headers = Json.decodeFromString<List<String>>(headersStr)
            val rows = Json.decodeFromString<List<CsvRow>>(rowsStr)
            KeyboardSession(uri.toString(), headers, rows, index)
        } catch (e: Exception) {
            null
        }
    }
}
