package com.mahavtaar.csvkeyboard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mahavtaar.csvkeyboard.data.model.ColumnConfig

@Dao
interface ColumnConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<ColumnConfig>)

    @Update
    suspend fun update(config: ColumnConfig)

    @Update
    suspend fun updateAll(configs: List<ColumnConfig>)

    @Query("SELECT * FROM column_configs ORDER BY `order` ASC")
    suspend fun getAll(): List<ColumnConfig>

    @Query("DELETE FROM column_configs")
    suspend fun deleteAll()
}
