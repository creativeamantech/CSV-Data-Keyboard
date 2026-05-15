package com.mahavtaar.csvkeyboard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mahavtaar.csvkeyboard.data.model.CsvRow

@Dao
interface CsvRowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CsvRow>)

    @Query("SELECT * FROM csv_rows ORDER BY rowIndex ASC")
    suspend fun getAll(): List<CsvRow>

    @Query("SELECT * FROM csv_rows WHERE rowIndex = :index")
    suspend fun getRowByIndex(index: Int): CsvRow?

    @Query("SELECT COUNT(*) FROM csv_rows")
    suspend fun getRowCount(): Int

    @Update
    suspend fun updateRow(row: CsvRow)

    @Query("DELETE FROM csv_rows")
    suspend fun deleteAll()
}
