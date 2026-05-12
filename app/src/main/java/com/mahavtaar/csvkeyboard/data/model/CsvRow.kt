package com.mahavtaar.csvkeyboard.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CsvRow(
    val rowIndex: Int,
    val data: Map<String, String>
)
