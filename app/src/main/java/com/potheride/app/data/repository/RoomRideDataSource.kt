package com.potheride.app.data.repository

import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.geo.GeoUtils
import com.potheride.app.core.geo.LatLng
import com.potheride.app.core.matching.MatchOutcome
import com.potheride.app.core.matching.MatchScorer
import com.potheride.app.core.matching.RouteMatcher
import com.potheride.app.core.pricing.FareCalculator
import com.potheride.app.core.pricing.PaymentMethod
import com.potheride.app.core.pricing.PaymentStatus
import com.potheride.app.core.pricing.PricingRules
import com.potheride.app.core.pricing.Taka
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.Actor
import com.potheride.app.core.ride.EtaCalculator
import com.potheride.app.core.ride.RideState
import com.potheride.app.core.ride.RideStateMachine
import com.potheride.app.core.ride.TransitionError
import com.potheride.app.data.local.AppDatabase
import com.potheride.app.data.local.entities.BookingEntity
import com.potheride.app.data.local.entities.DriverProfileEntity
import com.potheride.app.data.local.entities.MessageEntity
import com.potheride.app.data.local.entities.NotificationEntity
import com.potheride.app.data.local.entities.NotificationKind
import com.potheride.app.data.local.entities.PaymentEntity
import com.potheride.app.data.local.entities.RatingEntity
import com.potheride.app.data.local.entities.RouteWaypointEntity
import com.potheride.app.data.local.entities.SafetyEventEntity
import com.potheride.app.data.local.entities.SafetyEventKind
import com.potheride.app.data.local.entities.SavedPlaceEntity
import com.potheride.app.data.local.entities.TripEntity
import com.potheride.app.data.local.entities.TripStatus
import com.potheride.app.data.local.entities.TrustedContactEntity
import com.potheride.app.data.local.entities.UserEntity
import com.potheride.app.data.local.entities.VehicleEntity
import com.potheride.app.data.model.BookingDetail
import com.potheride.app.data.model.DriverSummary
import com.potheride.app.data.model.EarningsSummary
import com.potheride.app.data.model.MatchedRide
import com.potheride.app.data.model.PlatformStats
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID

/** Outcome of an operation that can legitimately fail for a business reason. */
sealed interface RepoResult<out T> {
    data class Ok<T>(val value: T) : RepoResult<T>
    data class Failed(val messageEn: String, val messageBn: String) : RepoResult<Nothing>
}

/**
 * The single seam between the UI and storage. Every rule that decides *what happens*
 * — matching, pricing, seat accounting, legal state transitions, notifications — is
 * applied here, so a screen can never put the system into an inconsistent state by
 * calling DAOs in the wrong order.
 *
 * Swapping this class's body for HTTP calls is the entire backend migration.
 */
class RoomRideDataSource(private val db: AppDatabase) : RideDataSource {

    // ------------------------------------------------------------------
    // Accounts
    // ------------------------------------------------------------------

    override suspend fun findOrCreateUser(phone: String, name: String, language: AppLanguage): UserEntity {
        db.userDao().findByPhone(phone)?.let { existing ->
            // Someone re-verifying an existing number may have changed their name.
            if (name.isNotBlank() && name != existing.name) {
                val updated = existing.copy(name = name, otpVerified = true)
                db.userDao().update(updated)
                return updated
            }
            return existing
        }
        val user = UserEntity(
            id = newId(),
            phone = phone,
            name = name,
            language = language.code,
            otpVerified = true,
            photoTint = phone.hashCode()
        )
        db.userDao().upsert(user)
        return user
    }

    override suspend fun findUser(id: String): UserEntity? = db.userDao().findById(id)

    /** Seed guard: cheap existence check that avoids materialising the whole row. */
    override suspend fun findUserByPhoneExists(phone: String): Boolean =
        db.userDao().findByPhone(phone) != null
    override fun observeUser(id: String): Flow<UserEntity?> = db.userDao().observe(id)
    override fun observeAllUsers(): Flow<List<UserEntity>> = db.userDao().observeAll()

    override suspend fun setLanguage(userId: String, language: AppLanguage) =
        db.userDao().setLanguage(userId, language.code)

    override suspend fun setUserBlocked(userId: String, blocked: Boolean) =
        db.userDao().setBlocked(userId, blocked)

    // ------------------------------------------------------------------
    // Driver profile and vehicles
    // ------------------------------------------------------------------

    override suspend fun findDriverForUser(userId: String): DriverProfileEntity? =
        db.driverDao().findByUserId(userId)

    override fun observeDriver(driverId: String): Flow<DriverProfileEntity?> = db.driverDao().observe(driverId)
    override fun observeAllDrivers(): Flow<List<DriverProfileEntity>> = db.driverDao().observeAll()

    override suspend fun becomeDriver(userId: String, licenseNumber: String): DriverProfileEntity {
        db.driverDao().findByUserId(userId)?.let { return it }
        val profile = DriverProfileEntity(id = newId(), userId = userId, licenseNumber = licenseNumber)
        db.driverDao().upsertDriver(profile)
        return profile
    }

    override suspend fun setDriverVerified(driverId: String, verified: Boolean) =
        db.driverDao().setVerified(driverId, verified)

    override fun vehiclesFor(driverId: String): Flow<List<VehicleEntity>> = db.driverDao().vehiclesFor(driverId)

    /**
     * Registers a vehicle, reusing the existing row when the driver re-enters a plate
     * they already own. Without this, publishing three trips in the same car would
     * leave three duplicate vehicles behind — and the unique index on plateNumber
     * would reject the second one outright.
     */
    override suspend fun registerVehicle(
        driverId: String,
        type: VehicleClass,
        plate: String,
        capacity: Int,
        model: String?
    ): VehicleEntity {
        val normalisedPlate = plate.trim().uppercase()
        db.driverDao().vehicleListFor(driverId)
            .firstOrNull { it.plateNumber.equals(normalisedPlate, ignoreCase = true) }
            ?.let { existing ->
                val updated = existing.copy(type = type, capacity = capacity, model = model)
                db.driverDao().upsertVehicle(updated)
                return updated
            }
        val vehicle = VehicleEntity(
            id = newId(), driverId = driverId, type = type,
            plateNumber = normalisedPlate, model = model, capacity = capacity
        )
        db.driverDao().upsertVehicle(vehicle)
        return vehicle
    }

    // ------------------------------------------------------------------
    // Publishing a route
    // ------------------------------------------------------------------

    override suspend fun publishTrip(
        driverId: String,
        vehicleId: String,
        startAddress: String,
        start: LatLng,
        endAddress: String,
        end: LatLng,
        departureTime: Long,
        seats: Int,
        detourKm: Double,
        waypoints: List<LatLng>
    ): TripEntity {
        // Always anchor the stored polyline to the real endpoints, whatever the caller
        // passed for intermediate points.
        val path = buildList {
            add(start)
            waypoints.forEach { if (it != start && it != end) add(it) }
            add(end)
        }

        val trip = TripEntity(
            id = newId(),
            driverId = driverId,
            vehicleId = vehicleId,
            startAddress = startAddress,
            startLat = start.lat, startLng = start.lng,
            endAddress = endAddress,
            endLat = end.lat, endLng = end.lng,
            departureTime = departureTime,
            totalSeats = seats,
            availableSeats = seats,
            detourKm = detourKm,
            currentLat = start.lat, currentLng = start.lng
        )
        db.tripDao().upsertTrip(trip)
        db.tripDao().insertWaypoints(
            path.mapIndexed { i, p ->
                RouteWaypointEntity(id = newId(), tripId = trip.id, seq = i, lat = p.lat, lng = p.lng)
            }
        )
        return trip
    }

    override suspend fun findTrip(tripId: String): TripEntity? = db.tripDao().findById(tripId)
    override fun observeTrip(tripId: String): Flow<TripEntity?> = db.tripDao().observeTrip(tripId)
    override fun tripsByDriver(driverId: String): Flow<List<TripEntity>> = db.tripDao().tripsByDriver(driverId)
    override fun activeTripForDriver(driverId: String): Flow<TripEntity?> = db.tripDao().activeTripForDriver(driverId)

    override suspend fun routeFor(tripId: String): List<LatLng> =
        db.tripDao().waypointsFor(tripId).map { it.point }

    override suspend fun setTripStatus(tripId: String, status: TripStatus) =
        db.tripDao().updateStatus(tripId, status)

    /**
     * Records a GPS fix against a trip. Progress is stored as distance travelled along
     * the published polyline rather than as a raw coordinate, because that is what
     * both the passenger's ETA and the map's progress indicator actually need, and it
     * stays correct if the driver's reported position wobbles off the road.
     */
    override suspend fun recordLocation(tripId: String, position: LatLng) {
        val route = routeFor(tripId)
        val travelled = if (route.size < 2) 0.0 else {
            val cumulative = GeoUtils.cumulativeKm(route)
            RouteMatcher.anchorOf(route, cumulative, position).distanceAlongRouteKm
        }
        db.tripDao().updateLiveLocation(
            tripId, position.lat, position.lng, travelled, System.currentTimeMillis()
        )
    }

    // ------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------

    /**
     * Runs the product's core promise: return only trips whose published path passes
     * close to *both* of the passenger's points, in the direction the driver is going,
     * priced by how much of the driver's own route the passenger shares.
     */
    override suspend fun searchMatches(
        pickup: LatLng,
        drop: LatLng,
        seatsNeeded: Int,
        earliestDeparture: Long,
        latestDeparture: Long,
        excludeDriverId: String?,
        rules: PricingRules
    ): List<MatchedRide> {
        val candidates = db.tripDao().findCandidateTrips(
            seatsNeeded = seatsNeeded,
            fromTime = earliestDeparture,
            toTime = latestDeparture,
            // A sentinel that matches no row, so the driver's own trips are excluded
            // when they are searching as a passenger.
            excludeDriverId = excludeDriverId ?: "\u0000none"
        )

        // One reference instant for the whole search, so two candidates are never
        // scored against clocks a few milliseconds apart.
        val now = System.currentTimeMillis()

        val results = mutableListOf<MatchedRide>()
        for (trip in candidates) {
            val route = routeFor(trip.id)
            val outcome = RouteMatcher.match(route, pickup, drop, trip.detourKm)
            if (outcome !is MatchOutcome.Matched) continue
            val match = outcome.result

            // This lookup is by driver-profile id, not user id. Getting it wrong here
            // silently drops every candidate and the search always returns empty.
            val driver = db.driverDao().findById(trip.driverId) ?: continue
            val vehicle = db.driverDao().findVehicleById(trip.vehicleId) ?: continue
            val user = db.userDao().findById(driver.userId) ?: continue
            if (user.blocked) continue
            if (seatsNeeded > vehicle.capacity) continue

            val hour = hourOf(trip.departureTime)
            val minutes = EtaCalculator.travelMinutes(match.sharedDistanceKm, vehicle.type, hour)
            val fare = FareCalculator.calculate(
                vehicleClass = vehicle.type,
                distanceKm = match.sharedDistanceKm,
                durationMinutes = minutes,
                seats = seatsNeeded,
                routeOverlapRatio = match.overlapRatio,
                departureHour = hour,
                rules = rules
            )
            val pickupEta = EtaCalculator.pickupEtaMillis(
                trip.departureTime, match.pickup.distanceAlongRouteKm, vehicle.type, hour
            )

            results.add(
                MatchedRide(
                    trip = trip,
                    driver = summarise(driver, user, vehicle),
                    route = route,
                    match = match,
                    fare = fare,
                    pickup = pickup,
                    drop = drop,
                    pickupEtaMillis = pickupEta,
                    dropoffEtaMillis = EtaCalculator.dropoffEtaMillis(
                        pickupEta, match.sharedDistanceKm, vehicle.type, hour
                    ),
                    score = MatchScorer.score(match, trip.departureTime, now)
                )
            )
        }

        // Best score first. Overlap alone used to decide this, which ranked a perfect-fit
        // ride leaving in three hours above a good-fit ride leaving in ten minutes —
        // see MatchScorer for what the score weighs. Ties still break on the earlier
        // departure, so the order is stable rather than arbitrary.
        return results.sortedWith(
            compareByDescending<MatchedRide> { it.score.value }
                .thenBy { it.trip.departureTime }
        )
    }

    // ------------------------------------------------------------------
    // Bookings
    // ------------------------------------------------------------------

    override suspend fun requestSeat(
        match: MatchedRide,
        passengerId: String,
        pickupAddress: String,
        dropAddress: String,
        seats: Int
    ): RepoResult<BookingEntity> {
        val trip = db.tripDao().findById(match.trip.id)
            ?: return RepoResult.Failed("This route is no longer available.", "এই রুটটি আর নেই।")
        if (trip.status != TripStatus.PUBLISHED && trip.status != TripStatus.IN_PROGRESS) {
            return RepoResult.Failed("This route has already ended.", "এই রুটটি শেষ হয়ে গেছে।")
        }
        if (trip.availableSeats < seats) {
            return RepoResult.Failed(
                "Only ${trip.availableSeats} seat(s) left on this route.",
                "এই রুটে আর ${trip.availableSeats}টি আসন বাকি আছে।"
            )
        }
        if (trip.driverId == db.driverDao().findByUserId(passengerId)?.id) {
            return RepoResult.Failed("You can't book your own route.", "নিজের রুটে আসন নেওয়া যাবে না।")
        }

        val booking = BookingEntity(
            id = newId(),
            tripId = trip.id,
            passengerId = passengerId,
            pickupAddress = pickupAddress,
            pickupLat = match.pickup.lat, pickupLng = match.pickup.lng,
            dropAddress = dropAddress,
            dropLat = match.drop.lat, dropLng = match.drop.lng,
            seatsRequested = seats,
            routeOverlapRatio = match.match.overlapRatio,
            sharedDistanceKm = match.match.sharedDistanceKm,
            detourKm = match.match.detourKm,
            farePoisha = match.fare.perSeatFare.poisha,
            totalPoisha = match.fare.totalFare.poisha,
            pickupEtaMillis = match.pickupEtaMillis,
            dropoffEtaMillis = match.dropoffEtaMillis
        )
        db.bookingDao().upsert(booking)

        val passenger = db.userDao().findById(passengerId)
        emit(
            RideNotifications.seatRequested(
                driverUserId = match.driver.userId,
                passengerName = passenger?.name,
                seats = seats,
                pickupAddress = pickupAddress,
                dropAddress = dropAddress,
                bookingId = booking.id
            )
        )
        return RepoResult.Ok(booking)
    }

    override fun requestsForTrip(tripId: String): Flow<List<BookingEntity>> = db.bookingDao().requestsForTrip(tripId)
    override fun bookingsForPassenger(userId: String): Flow<List<BookingEntity>> = db.bookingDao().bookingsForPassenger(userId)
    override fun bookingsForDriver(driverId: String): Flow<List<BookingEntity>> = db.bookingDao().bookingsForDriver(driverId)
    override fun activeBookingForPassenger(userId: String): Flow<BookingEntity?> = db.bookingDao().activeBookingForPassenger(userId)
    override fun observeBooking(id: String): Flow<BookingEntity?> = db.bookingDao().observe(id)
    override suspend fun findBooking(id: String): BookingEntity? = db.bookingDao().findById(id)

    /**
     * The only way a booking's status ever changes. Validates the transition against
     * the state machine first, then applies every side effect that must accompany it:
     * seat accounting, driver trip counts, and the notification to the other party.
     */
    override suspend fun transition(
        bookingId: String,
        to: RideState,
        actor: Actor,
        reason: String?
    ): RepoResult<BookingEntity> {
        val booking = db.bookingDao().findById(bookingId)
            ?: return RepoResult.Failed("That ride no longer exists.", "রাইডটি আর নেই।")

        RideStateMachine.validate(booking.status, to, actor)?.let { error: TransitionError ->
            return RepoResult.Failed(error.message, error.message)
        }

        val now = System.currentTimeMillis()
        db.bookingDao().setStatus(
            bookingId = bookingId,
            status = to,
            acceptedAt = if (to == RideState.ACCEPTED) now else booking.acceptedAt,
            completedAt = if (to == RideState.COMPLETED) now else booking.completedAt,
            reason = reason ?: booking.cancellationReason
        )

        if (RideStateMachine.consumesSeats(booking.status, to)) {
            db.tripDao().decrementSeats(booking.tripId, booking.seatsRequested)
        }
        if (RideStateMachine.releasesSeats(booking.status, to)) {
            db.tripDao().incrementSeats(booking.tripId, booking.seatsRequested)
        }

        val trip = db.tripDao().findById(booking.tripId)
        val driver = trip?.let { db.driverDao().findById(it.driverId) }
        val passenger = db.userDao().findById(booking.passengerId)

        if (to == RideState.COMPLETED && driver != null) {
            db.driverDao().incrementTrips(driver.id)
        }
        if (to == RideState.PICKED_UP && trip != null && trip.status == TripStatus.PUBLISHED) {
            db.tripDao().updateStatus(trip.id, TripStatus.IN_PROGRESS)
        }

        RideNotifications.forTransition(to, booking, driver?.userId, passenger?.name, reason)
            .forEach { emit(it) }

        return RepoResult.Ok(booking.copy(status = to))
    }

    /** Joins a booking with its trip, driver and route for the status and history screens. */
    override suspend fun detailFor(booking: BookingEntity): BookingDetail {
        val trip = db.tripDao().findById(booking.tripId)
        val driver = trip?.let { db.driverDao().findById(it.driverId) }
        val user = driver?.let { db.userDao().findById(it.userId) }
        val vehicle = trip?.let { db.driverDao().findVehicleById(it.vehicleId) }
        val passenger = db.userDao().findById(booking.passengerId)
        return BookingDetail(
            booking = booking,
            trip = trip,
            driver = if (driver != null && user != null && vehicle != null) {
                summarise(driver, user, vehicle)
            } else null,
            route = if (trip != null) routeFor(trip.id) else emptyList(),
            passengerName = passenger?.name
        )
    }

    // ------------------------------------------------------------------
    // Payments
    // ------------------------------------------------------------------

    /**
     * Settles a ride. Cash is marked complete immediately because the money changed
     * hands in the car; every gateway-backed method is recorded as PENDING and left
     * for a real integration to confirm, so the app never claims a transaction
     * succeeded when no gateway was called.
     */
    override suspend fun payForBooking(bookingId: String, method: PaymentMethod): RepoResult<PaymentEntity> {
        val booking = db.bookingDao().findById(bookingId)
            ?: return RepoResult.Failed("That ride no longer exists.", "রাইডটি আর নেই।")
        if (booking.status != RideState.COMPLETED) {
            return RepoResult.Failed(
                "Payment opens once the ride is complete.",
                "রাইড শেষ হলে পেমেন্ট করা যাবে।"
            )
        }
        val trip = db.tripDao().findById(booking.tripId)
            ?: return RepoResult.Failed("Route not found.", "রুট পাওয়া যায়নি।")

        val total = Taka.ofPoisha(booking.totalPoisha)
        val fee = (total * PricingRules.DEFAULT.platformCommission).roundedToTaka()
        val settledNow = !method.requiresGateway

        val existing = db.paymentDao().findForBooking(bookingId)
        val payment = PaymentEntity(
            id = existing?.id ?: newId(),
            bookingId = bookingId,
            driverId = trip.driverId,
            amountPoisha = total.poisha,
            platformFeePoisha = fee.poisha,
            driverEarningsPoisha = (total - fee).poisha,
            method = method,
            status = if (settledNow) PaymentStatus.COMPLETED else PaymentStatus.PENDING,
            transactionRef = if (settledNow) "CASH-${bookingId.take(8).uppercase()}" else null,
            paidAt = if (settledNow) System.currentTimeMillis() else null
        )
        db.paymentDao().upsert(payment)

        if (settledNow) {
            transition(bookingId, RideState.PAID, Actor.PASSENGER)
        }

        val driver = db.driverDao().findById(trip.driverId)
        driver?.let {
            notify(
                it.userId, NotificationKind.PAYMENT,
                if (settledNow) "Payment received" else "Payment pending",
                if (settledNow) "পেমেন্ট গৃহীত" else "পেমেন্ট অপেক্ষমাণ",
                if (settledNow) "You earned ${Taka.ofPoisha(payment.driverEarningsPoisha)} from this ride."
                else "${method.displayEn} settlement is pending confirmation.",
                if (settledNow) "এই রাইড থেকে আপনি ${Taka.ofPoisha(payment.driverEarningsPoisha)} আয় করেছেন।"
                else "${method.displayBn} পেমেন্ট নিশ্চিত হওয়ার অপেক্ষায়।",
                bookingId
            )
        }
        return RepoResult.Ok(payment)
    }

    /**
     * Confirms a gateway payment. In the MVP an admin taps this; with a live gateway
     * it is the webhook handler that calls it.
     */
    override suspend fun confirmGatewayPayment(bookingId: String, reference: String): RepoResult<Unit> {
        db.paymentDao().findForBooking(bookingId)
            ?: return RepoResult.Failed("No payment on record.", "কোনো পেমেন্ট পাওয়া যায়নি।")
        db.paymentDao().markSettled(bookingId, PaymentStatus.COMPLETED, System.currentTimeMillis(), reference)
        transition(bookingId, RideState.PAID, Actor.SYSTEM)
        return RepoResult.Ok(Unit)
    }

    override fun observePayment(bookingId: String): Flow<PaymentEntity?> = db.paymentDao().observeForBooking(bookingId)
    override fun observeRecentPayments(): Flow<List<PaymentEntity>> = db.paymentDao().observeRecent()

    override suspend fun earningsFor(driverId: String): EarningsSummary {
        val now = System.currentTimeMillis()
        val driver = db.driverDao().findById(driverId)
        return EarningsSummary(
            today = Taka.ofPoisha(db.paymentDao().earningsBetween(driverId, startOfToday(), now)),
            thisWeek = Taka.ofPoisha(db.paymentDao().earningsBetween(driverId, startOfWeek(), now)),
            thisMonth = Taka.ofPoisha(db.paymentDao().earningsBetween(driverId, startOfMonth(), now)),
            pending = Taka.ofPoisha(db.paymentDao().pendingEarnings(driverId)),
            completedTrips = db.paymentDao().completedPaymentCount(driverId),
            rating = driver?.rating,
            ratingCount = driver?.ratingCount ?: 0
        )
    }

    // ------------------------------------------------------------------
    // Ratings
    // ------------------------------------------------------------------

    override suspend fun submitRating(
        bookingId: String,
        raterId: String,
        rateeId: String,
        stars: Int,
        comment: String?
    ): RepoResult<Unit> {
        if (stars !in 1..5) {
            return RepoResult.Failed("Pick between 1 and 5 stars.", "১ থেকে ৫ তারকা বেছে নিন।")
        }
        if (db.ratingDao().countByRater(bookingId, raterId) > 0) {
            return RepoResult.Failed("You already rated this ride.", "আপনি এই রাইডে রেটিং দিয়েছেন।")
        }
        db.ratingDao().insert(
            RatingEntity(
                id = newId(), bookingId = bookingId, raterId = raterId,
                rateeId = rateeId, stars = stars, comment = comment?.takeIf { it.isNotBlank() }
            )
        )
        // Keep the driver's running average in sync when the person rated is a driver.
        db.driverDao().findByUserId(rateeId)?.let { db.driverDao().addRating(it.id, stars) }
        return RepoResult.Ok(Unit)
    }

    override fun ratingsFor(userId: String): Flow<List<RatingEntity>> = db.ratingDao().forUser(userId)

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    override fun messagesFor(bookingId: String): Flow<List<MessageEntity>> = db.messageDao().forBooking(bookingId)
    override fun unreadMessages(bookingId: String, readerId: String): Flow<Int> =
        db.messageDao().unreadCount(bookingId, readerId)

    override suspend fun markMessagesRead(bookingId: String, readerId: String) =
        db.messageDao().markRead(bookingId, readerId, System.currentTimeMillis())

    /** Chat only opens after a driver accepts — before that the two are strangers. */
    override suspend fun sendMessage(bookingId: String, senderId: String, content: String): RepoResult<Unit> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return RepoResult.Failed("Message is empty.", "বার্তা খালি।")
        val booking = db.bookingDao().findById(bookingId)
            ?: return RepoResult.Failed("That ride no longer exists.", "রাইডটি আর নেই।")
        if (booking.status == RideState.REQUESTED) {
            return RepoResult.Failed(
                "Chat opens once the driver accepts.",
                "চালক গ্রহণ করলে চ্যাট চালু হবে।"
            )
        }
        db.messageDao().insert(
            MessageEntity(id = newId(), bookingId = bookingId, senderId = senderId, content = trimmed.take(1000))
        )
        return RepoResult.Ok(Unit)
    }

    // ------------------------------------------------------------------
    // Saved places, trusted contacts, safety
    // ------------------------------------------------------------------

    override fun savedPlacesFor(userId: String): Flow<List<SavedPlaceEntity>> = db.savedPlaceDao().forUser(userId)

    override suspend fun savePlace(userId: String, label: String, address: String, point: LatLng) {
        val existing = db.savedPlaceDao().findByLabel(userId, label)
        db.savedPlaceDao().upsert(
            SavedPlaceEntity(
                id = existing?.id ?: newId(), userId = userId, label = label,
                address = address, lat = point.lat, lng = point.lng
            )
        )
    }

    override suspend fun deletePlace(id: String) = db.savedPlaceDao().delete(id)

    override fun trustedContactsFor(userId: String): Flow<List<TrustedContactEntity>> =
        db.trustedContactDao().forUser(userId)

    override suspend fun addTrustedContact(userId: String, name: String, phone: String) =
        db.trustedContactDao().upsert(
            TrustedContactEntity(id = newId(), userId = userId, name = name, phone = phone)
        )

    override suspend fun deleteTrustedContact(id: String) = db.trustedContactDao().delete(id)

    override suspend fun trustedContactList(userId: String): List<TrustedContactEntity> =
        db.trustedContactDao().listFor(userId)

    override suspend fun raiseSos(userId: String, bookingId: String?, position: LatLng?) {
        db.safetyDao().insert(
            SafetyEventEntity(
                id = newId(), raisedByUserId = userId, bookingId = bookingId,
                kind = SafetyEventKind.SOS, lat = position?.lat, lng = position?.lng,
                details = "Emergency raised from the ride screen"
            )
        )
        notify(
            userId, NotificationKind.SAFETY,
            "Emergency alert sent", "জরুরি সতর্কতা পাঠানো হয়েছে",
            "Your trusted contacts and our safety team have been notified.",
            "আপনার বিশ্বস্ত পরিচিতজন ও নিরাপত্তা টিমকে জানানো হয়েছে।",
            bookingId
        )
    }

    override suspend fun reportUser(reporterId: String, againstUserId: String, bookingId: String?, details: String) {
        db.safetyDao().insert(
            SafetyEventEntity(
                id = newId(), raisedByUserId = reporterId, againstUserId = againstUserId,
                bookingId = bookingId, kind = SafetyEventKind.REPORT, details = details
            )
        )
    }

    override suspend fun recordTripShared(userId: String, bookingId: String?, contactCount: Int) {
        db.safetyDao().insert(
            SafetyEventEntity(
                id = newId(), raisedByUserId = userId, bookingId = bookingId,
                kind = SafetyEventKind.TRIP_SHARED,
                details = "Shared with $contactCount trusted contact(s)"
            )
        )
    }

    override fun safetyEvents(): Flow<List<SafetyEventEntity>> = db.safetyDao().observeAll()
    override fun openSafetyCount(): Flow<Int> = db.safetyDao().openCount()
    override suspend fun resolveSafetyEvent(id: String) = db.safetyDao().resolve(id)

    // ------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------

    override fun notificationsFor(userId: String): Flow<List<NotificationEntity>> =
        db.notificationDao().forUser(userId)

    override fun unreadNotificationCount(userId: String): Flow<Int> = db.notificationDao().unreadCount(userId)

    override suspend fun markNotificationsRead(userId: String) =
        db.notificationDao().markAllRead(userId, System.currentTimeMillis())

    /** Persists one [PendingNotification] built by the shared [RideNotifications]. */
    private suspend fun emit(n: PendingNotification) =
        notify(n.userId, n.kind, n.titleEn, n.titleBn, n.bodyEn, n.bodyBn, n.bookingId)

    private suspend fun notify(
        userId: String,
        kind: NotificationKind,
        titleEn: String,
        titleBn: String,
        bodyEn: String,
        bodyBn: String,
        bookingId: String? = null
    ) {
        db.notificationDao().insert(
            NotificationEntity(
                id = newId(), userId = userId, kind = kind,
                titleEn = titleEn, titleBn = titleBn,
                bodyEn = bodyEn, bodyBn = bodyBn, bookingId = bookingId
            )
        )
    }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    override suspend fun platformStats(
        users: List<UserEntity>,
        drivers: List<DriverProfileEntity>,
        revenuePoisha: Long,
        openSafety: Int
    ): PlatformStats {
        return PlatformStats(
            totalUsers = users.size,
            totalDrivers = drivers.size,
            verifiedDrivers = drivers.count { it.verified },
            totalTrips = db.tripDao().count(),
            totalBookings = db.bookingDao().count(),
            completedRides = db.bookingDao().paidCount(),
            platformRevenue = Taka.ofPoisha(revenuePoisha),
            openSafetyEvents = openSafety
        )
    }

    override fun platformRevenue(): Flow<Long> = db.paymentDao().observePlatformRevenue()

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun summarise(
        driver: DriverProfileEntity,
        user: UserEntity,
        vehicle: VehicleEntity
    ) = DriverSummary(
        driverId = driver.id,
        userId = driver.userId,
        name = user.name,
        phone = user.phone,
        verified = driver.verified,
        rating = driver.rating,
        ratingCount = driver.ratingCount,
        totalTrips = driver.totalTrips,
        vehicleType = vehicle.type,
        vehiclePlate = vehicle.plateNumber,
        vehicleModel = vehicle.model,
        vehicleCapacity = vehicle.capacity
    )

    private fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfWeek(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    }.timeInMillis

    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
