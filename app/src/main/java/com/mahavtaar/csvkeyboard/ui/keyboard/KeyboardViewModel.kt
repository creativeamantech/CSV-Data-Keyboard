package com.mahavtaar.csvkeyboard.ui.keyboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mahavtaar.csvkeyboard.data.csv.CsvRepository
import com.mahavtaar.csvkeyboard.data.csv.SessionBus
import com.mahavtaar.csvkeyboard.data.model.CsvRow
import kotlinx.coroutines.launch

class KeyboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentRow = MutableLiveData<CsvRow?>()
    val currentRow: LiveData<CsvRow?> get() = _currentRow

    private val _sessionStats = MutableLiveData<Pair<Int, Int>>() // Pair(currentIndex, totalRows)
    val sessionStats: LiveData<Pair<Int, Int>> get() = _sessionStats

    init {
        viewModelScope.launch {
            SessionBus.rowChanged.collect {
                refreshSession()
            }
        }
    }

    fun refreshSession() {
        val session = CsvRepository.getSession() ?: CsvRepository.loadSessionFromPrefs(getApplication())
        session?.let {
            CsvRepository.initSession(it)
            if (it.rows.isNotEmpty()) {
                _currentRow.value = it.currentRow
                _sessionStats.value = Pair(it.currentIndex, it.totalRows)
            }
        }
    }

    fun goNext() {
        val session = CsvRepository.getSession() ?: return
        if (session.hasNext) {
            CsvRepository.updateIndex(getApplication(), session.currentIndex + 1)
        }
    }

    fun goPrevious() {
        val session = CsvRepository.getSession() ?: return
        if (session.hasPrevious) {
            CsvRepository.updateIndex(getApplication(), session.currentIndex - 1)
        }
    }
}
