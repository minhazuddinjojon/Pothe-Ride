package com.potheride.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.potheride.app.core.pricing.PaymentStatus
import com.potheride.app.core.ride.RideState
import com.potheride.app.data.local.entities.BookingEntity
import com.potheride.app.data.local.entities.DriverProfileEntity
import com.potheride.app.data.local.entities.MessageEntity
import com.potheride.app.data.local.entities.NotificationEntity
import com.potheride.app.data.local.entities.PaymentEntity
import com.potheride.app.data.local.entities.RatingEntity
import com.potheride.app.data.local.entities.RouteWaypointEntity
import com.potheride.app.data.local.entities.SafetyEventEntity
import com.potheride.app.data.local.entities.SavedPlaceEntity
import com.potheride.app.data.local.entities.TripEntity
import com.potheride.app.data.local.entities.TripStatus
import com.potheride.app.data.local.entities.TrustedContactEntity
import com.potheride.app.data.local.entities.UserEntity
import com.potheride.app.data.local.entities.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("UPDATE users SET language = :language WHERE id = :id")
    suspend fun setLanguage(id: String, language: String)

    @Query("UPDATE users SET blocked = :blocked WHERE id = :id")
    suspend fun setBlocked(id: String, blocked: Boolean)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int
}

@Dao
interface DriverDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDriver(driver: DriverProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicle(vehicle: VehicleEntity)

    @Query("SELECT * FROM driver_profiles WHERE userId = :userId LIMIT 1")
    suspend fun findByUserId(userId: String): DriverProfileEntity?

    @Query("SELECT * FROM driver_profiles WHERE id = :driverId LIMIT 1")
    suspend fun findById(driverId: String): DriverProfileEntity?

    @Query("SELECT * FROM driver_profiles WHERE id = :driverId LIMIT 1")
    fun observe(driverId: String): Flow<DriverProfileEntity?>

    @Query("SELECT * FROM driver_profiles ORDER BY verified ASC, totalTrips DESC")
    fun observeAll(): Flow<List<DriverProfileEntity>>

    @Query("SELECT * FROM vehicles WHERE driverId = :driverId")
    fun vehiclesFor(driverId: String): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE driverId = :driverId")
    suspend fun vehicleListFor(driverId: String): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId LIMIT 1")
    suspend fun findVehicleById(vehicleId: String): VehicleEntity?

    @Query("UPDATE driver_profiles SET verified = :verified WHERE id = :driverId")
    suspend fun setVerified(driverId: String, verified: Boolean)

    @Query("UPDATE driver_profiles SET totalTrips = totalTrips + 1 WHERE id = :driverId")
    suspend fun incrementTrips(driverId: String)

    /**
     * Ratings are stored as a running sum and count rather than a single average, so
     * adding a rating never needs to re-read and re-average the whole history.
     */
    @Query("UPDATE driver_profiles SET ratingSum = ratingSum + :stars, ratingCount = ratingCount + 1 WHERE id = :driverId")
    suspend fun addRating(driverId: String, stars: Int)
}

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoints(points: List<RouteWaypointEntity>)

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun findById(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    fun observeTrip(tripId: String): Flow<TripEntity?>

    /**
     * Coarse candidate pool for the matcher: right seat count, right time window,
     * still published. Geometry is filtered in Kotlin afterwards because SQLite has
     * no spatial functions, and the candidate set at city scale is small enough that
     * this is the right trade for the MVP.
     */
    @Query(
        """
        SELECT * FROM trips
        WHERE status = 'PUBLISHED'
          AND availableSeats >= :seatsNeeded
          AND departureTime BETWEEN :fromTime AND :toTime
          AND driverId != :excludeDriverId
        ORDER BY departureTime ASC
        LIMIT 100
        """
    )
    suspend fun findCandidateTrips(
        seatsNeeded: Int,
        fromTime: Long,
        toTime: Long,
        excludeDriverId: String
    ): List<TripEntity>

    @Query("SELECT * FROM route_waypoints WHERE tripId = :tripId ORDER BY seq ASC")
    suspend fun waypointsFor(tripId: String): List<RouteWaypointEntity>

    @Query("SELECT * FROM route_waypoints WHERE tripId = :tripId ORDER BY seq ASC")
    fun observeWaypoints(tripId: String): Flow<List<RouteWaypointEntity>>

    @Query("UPDATE trips SET currentLat = :lat, currentLng = :lng, travelledKm = :travelledKm, lastLocationAt = :at WHERE id = :tripId")
    suspend fun updateLiveLocation(tripId: String, lat: Double, lng: Double, travelledKm: Double, at: Long)

    @Query("UPDATE trips SET status = :status WHERE id = :tripId")
    suspend fun updateStatus(tripId: String, status: TripStatus)

    @Query("UPDATE trips SET availableSeats = MAX(0, availableSeats - :seats) WHERE id = :tripId")
    suspend fun decrementSeats(tripId: String, seats: Int)

    @Query("UPDATE trips SET availableSeats = MIN(totalSeats, availableSeats + :seats) WHERE id = :tripId")
    suspend fun incrementSeats(tripId: String, seats: Int)

    @Query("SELECT * FROM trips WHERE driverId = :driverId ORDER BY departureTime DESC")
    fun tripsByDriver(driverId: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE driverId = :driverId AND status IN ('PUBLISHED','IN_PROGRESS') ORDER BY departureTime ASC LIMIT 1")
    fun activeTripForDriver(driverId: String): Flow<TripEntity?>

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun count(): Int
}

@Dao
interface BookingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(booking: BookingEntity)

    @Query("SELECT * FROM bookings WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): BookingEntity?

    @Query("SELECT * FROM bookings WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<BookingEntity?>

    @Query("SELECT * FROM bookings WHERE tripId = :tripId ORDER BY requestedAt DESC")
    fun requestsForTrip(tripId: String): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE passengerId = :passengerId ORDER BY requestedAt DESC")
    fun bookingsForPassenger(passengerId: String): Flow<List<BookingEntity>>

    @Query(
        """
        SELECT b.* FROM bookings b
        INNER JOIN trips t ON b.tripId = t.id
        WHERE t.driverId = :driverId
        ORDER BY b.requestedAt DESC
        """
    )
    fun bookingsForDriver(driverId: String): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings WHERE passengerId = :passengerId AND status IN ('REQUESTED','ACCEPTED','DRIVER_ARRIVING','PICKED_UP','COMPLETED') ORDER BY requestedAt DESC LIMIT 1")
    fun activeBookingForPassenger(passengerId: String): Flow<BookingEntity?>

    @Query("UPDATE bookings SET status = :status, acceptedAt = :acceptedAt, completedAt = :completedAt, cancellationReason = :reason WHERE id = :bookingId")
    suspend fun setStatus(
        bookingId: String,
        status: RideState,
        acceptedAt: Long?,
        completedAt: Long?,
        reason: String?
    )

    @Query("SELECT COUNT(*) FROM bookings WHERE tripId = :tripId AND status IN ('ACCEPTED','DRIVER_ARRIVING','PICKED_UP')")
    suspend fun activeBookingCount(tripId: String): Int

    @Query("SELECT COUNT(*) FROM bookings")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM bookings WHERE status = 'PAID'")
    suspend fun paidCount(): Int
}

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: PaymentEntity)

    @Query("SELECT * FROM payments WHERE bookingId = :bookingId LIMIT 1")
    suspend fun findForBooking(bookingId: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE bookingId = :bookingId LIMIT 1")
    fun observeForBooking(bookingId: String): Flow<PaymentEntity?>

    @Query("UPDATE payments SET status = :status, paidAt = :paidAt, transactionRef = :ref WHERE bookingId = :bookingId")
    suspend fun markSettled(bookingId: String, status: PaymentStatus, paidAt: Long, ref: String?)

    /** Net driver earnings — after the platform's cut — settled within a window. */
    @Query("SELECT COALESCE(SUM(driverEarningsPoisha), 0) FROM payments WHERE driverId = :driverId AND status = 'COMPLETED' AND paidAt BETWEEN :from AND :to")
    suspend fun earningsBetween(driverId: String, from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(driverEarningsPoisha), 0) FROM payments WHERE driverId = :driverId AND status = 'PENDING'")
    suspend fun pendingEarnings(driverId: String): Long

    @Query("SELECT COUNT(*) FROM payments WHERE driverId = :driverId AND status = 'COMPLETED'")
    suspend fun completedPaymentCount(driverId: String): Int

    @Query("SELECT * FROM payments ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<PaymentEntity>>

    @Query("SELECT COALESCE(SUM(platformFeePoisha), 0) FROM payments WHERE status = 'COMPLETED'")
    fun observePlatformRevenue(): Flow<Long>
}

@Dao
interface RatingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rating: RatingEntity)

    @Query("SELECT * FROM ratings WHERE rateeId = :userId ORDER BY createdAt DESC")
    fun forUser(userId: String): Flow<List<RatingEntity>>

    @Query("SELECT AVG(stars) FROM ratings WHERE rateeId = :userId")
    suspend fun averageFor(userId: String): Double?

    @Query("SELECT COUNT(*) FROM ratings WHERE bookingId = :bookingId AND raterId = :raterId")
    suspend fun countByRater(bookingId: String, raterId: String): Int
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE bookingId = :bookingId ORDER BY sentAt ASC")
    fun forBooking(bookingId: String): Flow<List<MessageEntity>>

    @Query("UPDATE messages SET readAt = :at WHERE bookingId = :bookingId AND senderId != :readerId AND readAt IS NULL")
    suspend fun markRead(bookingId: String, readerId: String, at: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE bookingId = :bookingId AND senderId != :readerId AND readAt IS NULL")
    fun unreadCount(bookingId: String, readerId: String): Flow<Int>
}

@Dao
interface SavedPlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: SavedPlaceEntity)

    @Query("SELECT * FROM saved_places WHERE userId = :userId ORDER BY label ASC")
    fun forUser(userId: String): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places WHERE userId = :userId AND label = :label LIMIT 1")
    suspend fun findByLabel(userId: String, label: String): SavedPlaceEntity?

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TrustedContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: TrustedContactEntity)

    @Query("SELECT * FROM trusted_contacts WHERE userId = :userId ORDER BY name ASC")
    fun forUser(userId: String): Flow<List<TrustedContactEntity>>

    @Query("SELECT * FROM trusted_contacts WHERE userId = :userId")
    suspend fun listFor(userId: String): List<TrustedContactEntity>

    @Query("DELETE FROM trusted_contacts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC LIMIT 100")
    fun forUser(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND readAt IS NULL")
    fun unreadCount(userId: String): Flow<Int>

    @Query("UPDATE notifications SET readAt = :at WHERE userId = :userId AND readAt IS NULL")
    suspend fun markAllRead(userId: String, at: Long)
}

@Dao
interface SafetyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SafetyEventEntity)

    @Query("SELECT * FROM safety_events ORDER BY resolved ASC, createdAt DESC LIMIT 200")
    fun observeAll(): Flow<List<SafetyEventEntity>>

    @Query("SELECT * FROM safety_events WHERE raisedByUserId = :userId ORDER BY createdAt DESC")
    fun forUser(userId: String): Flow<List<SafetyEventEntity>>

    @Query("UPDATE safety_events SET resolved = 1 WHERE id = :id")
    suspend fun resolve(id: String)

    @Query("SELECT COUNT(*) FROM safety_events WHERE resolved = 0")
    fun openCount(): Flow<Int>
}

/**
 * Cross-table reads used by the passenger's results list and the driver's request
 * queue, where showing a booking without its trip, driver and vehicle would mean
 * four separate round trips per row.
 */
@Dao
interface JoinDao {
    @Transaction
    @Query(
        """
        SELECT t.*, d.userId AS driverUserId, u.name AS driverName, u.phone AS driverPhone,
               d.verified AS driverVerified, d.ratingSum AS driverRatingSum,
               d.ratingCount AS driverRatingCount, d.totalTrips AS driverTotalTrips,
               v.type AS vehicleType, v.plateNumber AS vehiclePlate, v.model AS vehicleModel,
               v.capacity AS vehicleCapacity
        FROM trips t
        INNER JOIN driver_profiles d ON t.driverId = d.id
        INNER JOIN users u ON d.userId = u.id
        INNER JOIN vehicles v ON t.vehicleId = v.id
        WHERE t.id = :tripId
        """
    )
    suspend fun tripDetail(tripId: String): TripDetailRow?
}

/** Flattened join row; mapped into a domain object by the repository. */
data class TripDetailRow(
    val id: String,
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
    val detourKm: Double,
    val currentLat: Double?,
    val currentLng: Double?,
    val travelledKm: Double,
    val lastLocationAt: Long?,
    val status: TripStatus,
    val createdAt: Long,
    val driverUserId: String,
    val driverName: String,
    val driverPhone: String,
    val driverVerified: Boolean,
    val driverRatingSum: Int,
    val driverRatingCount: Int,
    val driverTotalTrips: Int,
    val vehicleType: com.potheride.app.core.pricing.VehicleClass,
    val vehiclePlate: String,
    val vehicleModel: String?,
    val vehicleCapacity: Int
)
