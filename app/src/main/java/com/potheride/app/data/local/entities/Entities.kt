package com.potheride.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.potheride.app.core.geo.LatLng
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.core.pricing.PaymentStatus
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.RideState

/**
 * The whole persisted schema in one file. These are storage records only: business
 * rules live in [com.potheride.app.core], which has no Android dependency and is
 * covered by unit tests. Enums are stored as TEXT through Converters.
 */

@Entity(tableName = "users", indices = [Index(value = ["phone"], unique = true)])
data class UserEntity(
    @PrimaryKey val id: String,
    val phone: String,
    val name: String,
    val language: String = "en",
    val otpVerified: Boolean = false,
    val photoTint: Int = 0,
    val blocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "driver_profiles",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["userId"], unique = true)]
)
data class DriverProfileEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val licenseNumber: String,
    val verified: Boolean = false,
    val ratingSum: Int = 0,
    val ratingCount: Int = 0,
    val totalTrips: Int = 0
) {
    /** New drivers show as unrated rather than as a misleading 5.0. */
    val rating: Float? get() = if (ratingCount == 0) null else ratingSum.toFloat() / ratingCount
}

@Entity(
    tableName = "vehicles",
    foreignKeys = [ForeignKey(
        entity = DriverProfileEntity::class, parentColumns = ["id"], childColumns = ["driverId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("driverId"), Index(value = ["plateNumber"], unique = true)]
)
data class VehicleEntity(
    @PrimaryKey val id: String,
    val driverId: String,
    val type: VehicleClass,
    val plateNumber: String,
    val model: String? = null,
    val colour: String? = null,
    val capacity: Int
)

enum class TripStatus { PUBLISHED, IN_PROGRESS, COMPLETED, CANCELLED }

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = DriverProfileEntity::class, parentColumns = ["id"], childColumns = ["driverId"],
            onDelete = ForeignKey.CASCADE
        ),
        // RESTRICT, not SET_NULL: vehicleId is non-null, and Room rejects SET_NULL on a
        // non-null column at compile time.
        ForeignKey(
            entity = VehicleEntity::class, parentColumns = ["id"], childColumns = ["vehicleId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("driverId"), Index("vehicleId"), Index("status"), Index("departureTime")]
)
data class TripEntity(
    @PrimaryKey val id: String,
    val driverId: String,
    val vehicleId: String,
    val startAddress: String,
    val startLat: Double,
    val startLng: Double,
    val endAddress: String,
    val endLat: Double,
    val endLng: Double,
    val departureTime: Long,
    val totalSeats: Int,
    val availableSeats: Int,
    val detourKm: Double = 1.5,
    val currentLat: Double? = null,
    val currentLng: Double? = null,
    val travelledKm: Double = 0.0,
    val lastLocationAt: Long? = null,
    val status: TripStatus = TripStatus.PUBLISHED,
    val createdAt: Long = System.currentTimeMillis()
) {
    val startPoint: LatLng get() = LatLng(startLat, startLng)
    val endPoint: LatLng get() = LatLng(endLat, endLng)
    val livePoint: LatLng?
        get() = if (currentLat != null && currentLng != null) LatLng(currentLat, currentLng) else null
}

@Entity(
    tableName = "route_waypoints",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("tripId")]
)
data class RouteWaypointEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val seq: Int,
    val lat: Double,
    val lng: Double,
    val placeName: String? = null
) {
    val point: LatLng get() = LatLng(lat, lng)
}

@Entity(
    tableName = "bookings",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class, parentColumns = ["id"], childColumns = ["passengerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId"), Index("passengerId"), Index("status")]
)
data class BookingEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val passengerId: String,
    val pickupAddress: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropAddress: String,
    val dropLat: Double,
    val dropLng: Double,
    val seatsRequested: Int = 1,
    val routeOverlapRatio: Float = 0f,
    val sharedDistanceKm: Double = 0.0,
    val detourKm: Double = 0.0,
    val farePoisha: Long = 0,
    val totalPoisha: Long = 0,
    val pickupEtaMillis: Long? = null,
    val dropoffEtaMillis: Long? = null,
    val status: RideState = RideState.REQUESTED,
    val cancellationReason: String? = null,
    val requestedAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long? = null,
    val completedAt: Long? = null
) {
    val pickupPoint: LatLng get() = LatLng(pickupLat, pickupLng)
    val dropPoint: LatLng get() = LatLng(dropLat, dropLng)
}

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = BookingEntity::class, parentColumns = ["id"], childColumns = ["bookingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["bookingId"], unique = true), Index("status")]
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val bookingId: String,
    val driverId: String,
    val amountPoisha: Long,
    val platformFeePoisha: Long,
    val driverEarningsPoisha: Long,
    val method: PaymentMethod,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val transactionRef: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val paidAt: Long? = null
)

@Entity(
    tableName = "ratings",
    foreignKeys = [ForeignKey(
        entity = BookingEntity::class, parentColumns = ["id"], childColumns = ["bookingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookingId"), Index("rateeId")]
)
data class RatingEntity(
    @PrimaryKey val id: String,
    val bookingId: String,
    val raterId: String,
    val rateeId: String,
    val stars: Int,
    val comment: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = BookingEntity::class, parentColumns = ["id"], childColumns = ["bookingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookingId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val bookingId: String,
    val senderId: String,
    val content: String,
    val readAt: Long? = null,
    val sentAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "saved_places",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId")]
)
data class SavedPlaceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val label: String,
    val address: String,
    val lat: Double,
    val lng: Double
) {
    val point: LatLng get() = LatLng(lat, lng)
}

/** Someone who receives a trip-share link and any SOS alert the user raises. */
@Entity(
    tableName = "trusted_contacts",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId")]
)
data class TrustedContactEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val phone: String
)

enum class NotificationKind {
    RIDE_REQUEST, REQUEST_ACCEPTED, REQUEST_DECLINED, DRIVER_ARRIVING,
    RIDE_COMPLETED, PAYMENT, CANCELLATION, ROUTE_CHANGE, SAFETY, SYSTEM
}

@Entity(
    tableName = "notifications",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId"), Index("readAt")]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val kind: NotificationKind,
    val titleEn: String,
    val titleBn: String,
    val bodyEn: String,
    val bodyBn: String,
    val bookingId: String? = null,
    val readAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class SafetyEventKind { SOS, REPORT, TRIP_SHARED, BLOCK }

@Entity(tableName = "safety_events", indices = [Index("raisedByUserId"), Index("kind")])
data class SafetyEventEntity(
    @PrimaryKey val id: String,
    val raisedByUserId: String,
    val againstUserId: String? = null,
    val bookingId: String? = null,
    val kind: SafetyEventKind,
    val details: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val resolved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
