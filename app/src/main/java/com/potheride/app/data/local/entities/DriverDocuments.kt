package com.potheride.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.potheride.app.core.verification.DriverDocument
import com.potheride.app.core.verification.DriverDocumentKind
import com.potheride.app.core.verification.DriverDocumentStatus

/**
 * The Room record for one uploaded verification document.
 *
 * Kept apart from the enums it stores: [DriverDocumentKind] and [DriverDocumentStatus]
 * live in `core/verification`, which has no Room dependency, so the approval rules that
 * gate publishing can be unit-tested on the JVM without a database. This entity is the
 * only file that knows both worlds — it stores the enums by `name` (see [Converters])
 * and exposes [toDomain] to cross back into the pure side.
 */
@Entity(
    tableName = "driver_documents",
    foreignKeys = [ForeignKey(
        entity = DriverProfileEntity::class, parentColumns = ["id"], childColumns = ["driverId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("driverId"), Index("kind")]
)
data class DriverDocumentEntity(
    @PrimaryKey val id: String,
    val driverId: String,
    val kind: DriverDocumentKind,
    /** Firebase Storage path, or a local file URI while offline — see [FirestoreSchema.Storage]. */
    val storagePath: String,
    val status: DriverDocumentStatus = DriverDocumentStatus.PENDING,
    val rejectionReason: String? = null,
    val uploadedAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null
) {
    /** Crosses into the pure domain model the approval rules actually consume. */
    fun toDomain(): DriverDocument = DriverDocument(
        kind = kind, status = status, uploadedAt = uploadedAt, rejectionReason = rejectionReason
    )
}
