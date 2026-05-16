package com.mahavtaar.csvkeyboard.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ColumnConfig(
    val columnName: String,
    val mode: ColumnMode,
    val displayLabel: String,
    val order: Int,
    val textSizeMultiplier: Float = 1.0f,
    val colorHex: String = "#FFFFFF"
)
