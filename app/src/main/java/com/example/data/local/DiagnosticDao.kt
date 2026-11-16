package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticDao {
    @Query("SELECT * FROM diagnostics ORDER BY timestampMillis DESC")
    fun getAllDiagnostics(): Flow<List<DiagnosticEntity>>

    @Query("SELECT * FROM diagnostics WHERE id = :id LIMIT 1")
    suspend fun getDiagnosticById(id: Long): DiagnosticEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnostic(entity: DiagnosticEntity): Long

    @Query("DELETE FROM diagnostics WHERE id = :id")
    suspend fun deleteDiagnosticById(id: Long)
}
