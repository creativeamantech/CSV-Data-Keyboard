package com.mahavtaar.csvkeyboard.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "column_configs")
data class ColumnConfig(
    @PrimaryKey val columnName: String,
    val mode: ColumnMode,
    val displayLabel: String,
    val order: Int,
    val textSizeMultiplier: Float = 1.0f,
    val colorHex: String = "#FFFFFF"
)
