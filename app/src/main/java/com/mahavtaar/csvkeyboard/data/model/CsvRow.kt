package com.mahavtaar.csvkeyboard.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "csv_rows")
data class CsvRow(
    @PrimaryKey val rowIndex: Int,
    val data: Map<String, String>,
    val isDone: Boolean = false
)
