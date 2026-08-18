package com.potheride.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.potheride.app.data.local.entities.DriverDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverDocumentDao {
    @Upsert
    suspend fun upsert(document: DriverDocumentEntity)

    @Query("SELECT * FROM driver_documents WHERE driverId = :driverId ORDER BY uploadedAt DESC")
    fun forDriver(driverId: String): Flow<List<DriverDocumentEntity>>

    @Query("SELECT * FROM driver_documents WHERE driverId = :driverId ORDER BY uploadedAt DESC")
    suspend fun forDriverOnce(driverId: String): List<DriverDocumentEntity>

    @Query(
        "UPDATE driver_documents SET status = :status, rejectionReason = :reason, reviewedAt = :reviewedAt " +
            "WHERE id = :id"
    )
    suspend fun review(id: String, status: String, reason: String?, reviewedAt: Long)
}
