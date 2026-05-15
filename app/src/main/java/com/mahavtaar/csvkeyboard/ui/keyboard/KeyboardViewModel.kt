package com.mahavtaar.csvkeyboard.ui.keyboard

import android.content.Context
import com.mahavtaar.csvkeyboard.data.csv.CsvRepository
import com.mahavtaar.csvkeyboard.data.csv.SessionBus
import com.mahavtaar.csvkeyboard.data.model.CsvRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KeyboardViewModel(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : SessionBus.RowChangeListener {

    private val _currentRow = MutableStateFlow<CsvRow?>(null)
    val currentRow: StateFlow<CsvRow?> = _currentRow.asStateFlow()

    private val _sessionStats = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0)) // Pair(currentIndex, totalRows)
    val sessionStats: StateFlow<Pair<Int, Int>> = _sessionStats.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    private var allRows = listOf<CsvRow>()
    private var filteredRows = listOf<CsvRow>()

    init {
        SessionBus.init(context)
        SessionBus.addListener(this)
        refreshSession()
    }

    override fun onRowChanged(newIndex: Int) {
        refreshSession()
    }

    fun refreshSession() {
        coroutineScope.launch {
            val session = CsvRepository.getSession() ?: CsvRepository.loadSession(context)
            session?.let {
                CsvRepository.initSession(it)
                allRows = it.rows
                applyFilter()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilter()
    }

    private fun applyFilter() {
        val query = _searchQuery.value.trim().lowercase()
        filteredRows = if (query.isEmpty()) {
            allRows
        } else {
            allRows.filter { row ->
                row.data.values.any { value -> value.lowercase().contains(query) }
            }
        }

        val session = CsvRepository.getSession()
        if (session != null && filteredRows.isNotEmpty()) {
            // Find current row in filtered list, or default to 0
            var newIndex = filteredRows.indexOfFirst { it.rowIndex == session.currentRow.rowIndex }
            if (newIndex == -1) newIndex = 0

            _currentRow.value = filteredRows[newIndex]
            _sessionStats.value = Pair(newIndex, filteredRows.size)
        } else {
            _currentRow.value = null
            _sessionStats.value = Pair(0, 0)
        }
    }

    fun goNext() {
        if (filteredRows.isEmpty()) return
        val currentIdx = _sessionStats.value.first
        if (currentIdx < filteredRows.size - 1) {
            val nextRow = filteredRows[currentIdx + 1]
            CsvRepository.updateIndex(context, nextRow.rowIndex)
        }
    }

    fun goPrevious() {
        if (filteredRows.isEmpty()) return
        val currentIdx = _sessionStats.value.first
        if (currentIdx > 0) {
            val prevRow = filteredRows[currentIdx - 1]
            CsvRepository.updateIndex(context, prevRow.rowIndex)
        }
    }

    fun markRowDone() {
        coroutineScope.launch {
            val row = _currentRow.value ?: return@launch
            val updatedRow = row.copy(isDone = !row.isDone)
            CsvRepository.updateRow(context, updatedRow)
            refreshSession()
        }
    }

    fun clear() {
        SessionBus.removeListener(this)
    }
}
