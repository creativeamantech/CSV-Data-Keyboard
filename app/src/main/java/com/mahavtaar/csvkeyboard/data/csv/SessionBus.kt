package com.mahavtaar.csvkeyboard.data.csv

import android.content.Context
import android.content.SharedPreferences
import com.mahavtaar.csvkeyboard.data.prefs.AppPreferences

object SessionBus {

    interface RowChangeListener {
        fun onRowChanged(newIndex: Int)
    }

    private val listeners = mutableListOf<RowChangeListener>()
    private var sharedPreferences: SharedPreferences? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == AppPreferences.KEY_CHANGE_STAMP) {
            val newIndex = prefs.getInt(AppPreferences.KEY_CURRENT_ROW, 0)
            listeners.forEach { it.onRowChanged(newIndex) }
        }
    }

    fun init(context: Context) {
        if (sharedPreferences == null) {
            sharedPreferences = AppPreferences.getPrefs(context)
            sharedPreferences?.registerOnSharedPreferenceChangeListener(prefListener)
        }
    }

    fun addListener(listener: RowChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: RowChangeListener) {
        listeners.remove(listener)
    }

    fun emitRowChange(context: Context, newIndex: Int) {
        AppPreferences.save(context, AppPreferences.KEY_CURRENT_ROW, newIndex)
        AppPreferences.save(context, AppPreferences.KEY_CHANGE_STAMP, System.currentTimeMillis())
    }
}
