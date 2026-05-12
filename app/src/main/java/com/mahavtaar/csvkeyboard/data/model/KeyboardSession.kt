package com.mahavtaar.csvkeyboard.data.model

data class KeyboardSession(
    val filePath: String,
    val headers: List<String>,
    val rows: List<CsvRow>,
    val currentIndex: Int = 0,
    val totalRows: Int = rows.size
) {
    val currentRow: CsvRow get() = rows[currentIndex]
    val hasPrevious: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex < totalRows - 1
}
