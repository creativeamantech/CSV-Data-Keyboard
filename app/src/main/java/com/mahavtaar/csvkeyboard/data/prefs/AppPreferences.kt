package com.mahavtaar.csvkeyboard.data.prefs

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREFS_NAME = "csv_keyboard_prefs"

    const val KEY_CSV_URI = "csv_uri"
    const val KEY_CURRENT_ROW = "current_row_index"
    const val KEY_COLUMN_CONFIGS = "column_configs_json"
    const val KEY_CSV_HEADERS = "csv_headers_json"
    const val KEY_DELIMITER = "csv_delimiter"
    const val KEY_BALL_ENABLED = "ball_enabled"
    const val KEY_CSV_ROWS = "csv_rows_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun save(context: Context, key: String, value: String?) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun save(context: Context, key: String, value: Int) {
        getPrefs(context).edit().putInt(key, value).apply()
    }

    fun save(context: Context, key: String, value: Boolean) {
        getPrefs(context).edit().putBoolean(key, value).apply()
    }

    fun getString(context: Context, key: String, defaultValue: String? = null): String? {
        return getPrefs(context).getString(key, defaultValue)
    }

    fun getInt(context: Context, key: String, defaultValue: Int = 0): Int {
        return getPrefs(context).getInt(key, defaultValue)
    }

    fun getBoolean(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return getPrefs(context).getBoolean(key, defaultValue)
    }
}
