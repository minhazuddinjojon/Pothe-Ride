package com.potheride.app.data.repository

import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.geo.LatLng
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.core.pricing.PricingRules
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.Actor
import com.potheride.app.core.ride.RideState
import com.potheride.app.data.local.entities.BookingEntity
import com.potheride.app.data.local.entities.DriverProfileEntity
import com.potheride.app.data.local.entities.MessageEntity
import com.potheride.app.data.local.entities.NotificationEntity
import com.potheride.app.data.local.entities.PaymentEntity
import com.potheride.app.data.local.entities.RatingEntity
import com.potheride.app.data.local.entities.SafetyEventEntity
import com.potheride.app.data.local.entities.SavedPlaceEntity
import com.potheride.app.data.local.entities.TripEntity
import com.potheride.app.data.local.entities.TripStatus
import com.potheride.app.data.local.entities.TrustedContactEntity
import com.potheride.app.data.local.entities.UserEntity
import com.potheride.app.data.local.entities.VehicleEntity
import com.potheride.app.data.model.BookingDetail
import com.potheride.app.data.model.EarningsSummary
import com.potheride.app.data.model.MatchedRide
import com.potheride.app.data.model.PlatformStats
import kotlinx.coroutines.flow.Flow

/**
 * Everything the app can ask of its data layer.
 *
 * Extracted from [RoomRideDataSource] so a second implementation — Firestore — can be
 * dropped in without the UI knowing which one it is talking to. The signatures are
 * unchanged from the original class: this is a seam, not a redesign.
 *
 * Two properties of the surface make it backend-agnostic, and both were already true
 * of the Room implementation:
 *  - reads the UI watches return [Flow], which Firestore snapshot listeners model just
 *    as naturally as Room queries;
 *  - operations that can fail for a *business* reason return [RepoResult] rather than
 *    throwing, so "no seats left" and "the network is down" stay distinguishable.
 *
 * Default argument values live here, on the interface, and are deliberately absent from
 * the implementations — Kotlin forbids an override from restating them, and having them
 * in one place is what stops two backends from disagreeing about what "no filter" means.
 */
interface RideDataSource {

    suspend fun findOrCreateUser(phone: String, name: String, language: AppLanguage): UserEntity

    suspend fun findUser(id: String): UserEntity?

    suspend fun findUserByPhoneExists(phone: String): Boolean

    fun observeUser(id: String): Flow<UserEntity?>

    fun observeAllUsers(): Flow<List<UserEntity>>

    suspend fun setLanguage(userId: String, language: AppLanguage)

    suspend fun setUserBlocked(userId: String, blocked: Boolean)

    suspend fun findDriverForUser(userId: String): DriverProfileEntity?

    fun observeDriver(driverId: String): Flow<DriverProfileEntity?>

    fun observeAllDrivers(): Flow<List<DriverProfileEntity>>

    suspend fun becomeDriver(userId: String, licenseNumber: String): DriverProfileEntity

    suspend fun setDriverVerified(driverId: String, verified: Boolean)

    fun vehiclesFor(driverId: String): Flow<List<VehicleEntity>>

    suspend fun registerVehicle( driverId: String, type: VehicleClass, plate: String, capacity: Int, model: String? = null ): VehicleEntity

    suspend fun publishTrip( driverId: String, vehicleId: String, startAddress: String, start: LatLng, endAddress: String, end: LatLng, departureTime: Long, seats: Int, detourKm: Double, waypoints: List<LatLng> ): TripEntity

    suspend fun findTrip(tripId: String): TripEntity?

    fun observeTrip(tripId: String): Flow<TripEntity?>

    fun tripsByDriver(driverId: String): Flow<List<TripEntity>>

    fun activeTripForDriver(driverId: String): Flow<TripEntity?>

    suspend fun routeFor(tripId: String): List<LatLng>

    suspend fun setTripStatus(tripId: String, status: TripStatus)

    suspend fun recordLocation(tripId: String, position: LatLng)

    suspend fun searchMatches( pickup: LatLng, drop: LatLng, seatsNeeded: Int, earliestDeparture: Long, latestDeparture: Long, excludeDriverId: String? = null, rules: PricingRules = PricingRules.DEFAULT ): List<MatchedRide>

    suspend fun requestSeat( match: MatchedRide, passengerId: String, pickupAddress: String, dropAddress: String, seats: Int ): RepoResult<BookingEntity>

    fun requestsForTrip(tripId: String): Flow<List<BookingEntity>>

    fun bookingsForPassenger(userId: String): Flow<List<BookingEntity>>

    fun bookingsForDriver(driverId: String): Flow<List<BookingEntity>>

    fun activeBookingForPassenger(userId: String): Flow<BookingEntity?>

    fun observeBooking(id: String): Flow<BookingEntity?>

    suspend fun findBooking(id: String): BookingEntity?

    suspend fun transition( bookingId: String, to: RideState, actor: Actor, reason: String? = null ): RepoResult<BookingEntity>

    suspend fun detailFor(booking: BookingEntity): BookingDetail

    suspend fun payForBooking(bookingId: String, method: PaymentMethod): RepoResult<PaymentEntity>

    suspend fun confirmGatewayPayment(bookingId: String, reference: String): RepoResult<Unit>

    fun observePayment(bookingId: String): Flow<PaymentEntity?>

    fun observeRecentPayments(): Flow<List<PaymentEntity>>

    suspend fun earningsFor(driverId: String): EarningsSummary

    suspend fun submitRating( bookingId: String, raterId: String, rateeId: String, stars: Int, comment: String? ): RepoResult<Unit>

    fun ratingsFor(userId: String): Flow<List<RatingEntity>>

    fun messagesFor(bookingId: String): Flow<List<MessageEntity>>

    fun unreadMessages(bookingId: String, readerId: String): Flow<Int>

    suspend fun markMessagesRead(bookingId: String, readerId: String)

    suspend fun sendMessage(bookingId: String, senderId: String, content: String): RepoResult<Unit>

    fun savedPlacesFor(userId: String): Flow<List<SavedPlaceEntity>>

    suspend fun savePlace(userId: String, label: String, address: String, point: LatLng)

    suspend fun deletePlace(id: String)

    fun trustedContactsFor(userId: String): Flow<List<TrustedContactEntity>>

    suspend fun addTrustedContact(userId: String, name: String, phone: String)

    suspend fun deleteTrustedContact(id: String)

    suspend fun trustedContactList(userId: String): List<TrustedContactEntity>

    suspend fun raiseSos(userId: String, bookingId: String?, position: LatLng?)

    suspend fun reportUser(reporterId: String, againstUserId: String, bookingId: String?, details: String)

    suspend fun recordTripShared(userId: String, bookingId: String?, contactCount: Int)

    fun safetyEvents(): Flow<List<SafetyEventEntity>>

    fun openSafetyCount(): Flow<Int>

    suspend fun resolveSafetyEvent(id: String)

    fun notificationsFor(userId: String): Flow<List<NotificationEntity>>

    fun unreadNotificationCount(userId: String): Flow<Int>

    suspend fun markNotificationsRead(userId: String)

    suspend fun platformStats( users: List<UserEntity>, drivers: List<DriverProfileEntity>, revenuePoisha: Long, openSafety: Int ): PlatformStats

    fun platformRevenue(): Flow<Long>
}
